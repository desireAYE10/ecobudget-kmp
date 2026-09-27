# EcoBudget 🌿 — Migration vers Kotlin Multiplatform (KMP) + Compose Multiplatform (CMP)

> UE Développement mobile avancé · Année académique 2025-2026
> Migration du prototype Android natif vers une architecture partagée `shared` (Android + iOS),
> l'application Android finale consommant ce socle commun.

## 1. Vue d'ensemble de l'architecture

```
EcoBudget/
├── shared/                         (module KMP — Android + iOS)
│   └── src/
│       ├── commonMain/kotlin/com/example/shared/
│       │   ├── model/              Category, Transaction, YearMonth
│       │   ├── data/repository/    TransactionRepository, FakeTransactionRepository
│       │   └── presentation/       EcoBudgetUiState, EcoBudgetViewModel
│       ├── commonMain/composeResources/values/strings.xml   (catalogue de textes partagé)
│       ├── commonTest/kotlin/...   Tests de non-régression (YearMonth)
│       ├── androidMain/            (vide — aucune dépendance Android n'a été nécessaire)
│       └── iosMain/                (vide — même raison, cible déclarée pour l'export du framework)
└── app/                             (application Android native — UI uniquement)
    └── src/main/java/com/example/
        ├── MainActivity.kt
        └── ui/                     screens, components, theme (Jetpack Compose)
```

**Principe directeur** : `commonMain` ne contient plus une seule ligne dépendant de `java.*`,
`android.*`, ou de la classe générée `com.example.R`. L'application Android (`app`) ne contient
plus que de l'interface graphique (Composables) ; toute la logique métier, l'état et les
ressources textuelles viennent de `:shared`.

---

## 2. Mise en place de l'infrastructure partagée

**Problème rencontré** : le projet de départ est une appli Android pure (`com.android.application`
+ `org.jetbrains.kotlin.android`), sans notion de cible multiplateforme. Il n'existe aucune façon
d'y écrire du code compilé à la fois pour Android et pour Kotlin/Native (iOS).

**Choix technique** :
- Ajout d'un module `:shared` avec les plugins `org.jetbrains.kotlin.multiplatform`,
  `com.android.library` et `org.jetbrains.compose` (Compose Multiplatform), déclarant les cibles
  `androidTarget()`, `iosX64()`, `iosArm64()` et `iosSimulatorArm64()`.
- Kotlin a été monté de `2.0.21` à **`2.1.10`** et le plugin Compose Multiplatform fixé en
  **`1.7.3`**, une combinaison stable et documentée (la 1.7.x est la dernière branche de CMP
  conçue pour du Kotlin 2.0.x/2.1.x ; les branches 1.9.x/1.10.x exigent Kotlin ≥ 2.1.20).
- Ajout de `org.jetbrains.androidx.lifecycle:lifecycle-viewmodel:2.8.4` (fork multiplateforme
  du ViewModel Android, voir §4) et `org.jetbrains.kotlinx:kotlinx-datetime:0.6.1`.
- Suppression du plugin KSP et des dépendances Room dans `app/build.gradle.kts` : une
  vérification (`grep -r "androidx.room" app/src`) a confirmé qu'aucune classe `@Entity`/`@Dao`
  n'était utilisée nulle part dans le code source — c'était un résidu du template de départ
  (le commentaire du fichier l'indiquait déjà : *« à retirer si Room n'est plus utilisé »*).

**Justification** : c'est l'unique façon d'obtenir un `commonMain` réellement compilable pour
Android **et** iOS, conformément à l'objectif de l'énoncé. Documenter précisément les versions
évite l'erreur la plus fréquente sur ce type de migration (incompatibilité Kotlin ↔ Compose
Compiler ↔ Compose Multiplatform).

---

## 3. Migration de la couche Domaine (`model`)

### 3.1 `Category.kt`

**Problème rencontré** : l'énumération référence `com.example.R.string.category_transport`
(etc.), une classe générée exclusivement par l'Android Gradle Plugin à partir de
`res/values/strings.xml`. Cette classe n'existe pas en `commonMain`.

```kotlin
// Avant (Android uniquement)
enum class Category(val labelResId: Int, val emoji: String) {
    TRANSPORT(R.string.category_transport, "🚌"), ...
}
```

**Choix technique** : remplacement du type `Int` (id de ressource Android) par un
`org.jetbrains.compose.resources.StringResource`, pointant vers le nouveau catalogue partagé
`shared/src/commonMain/composeResources/values/strings.xml` (voir §5). Le nom du champ a été
renommé `labelRes` pour refléter le changement de type.

**Justification** : `StringResource` est le type multiplateforme officiel de Compose
Multiplatform Resources ; il se résout aussi bien sur Android que sur iOS via la même fonction
`stringResource(...)`, sans aucune dépendance à `android.content.res.Resources`.

### 3.2 `Transaction.kt`

**Problème rencontré** : aucun. La classe ne dépendait déjà que de types Kotlin natifs
(`String`, `Double`, `Long`).

**Choix technique** : déplacement à l'identique vers `commonMain`.

**Justification** : rien à adapter — c'est le cas le plus simple, cité ici pour montrer que
tous les fichiers n'ont pas nécessité le même effort.

### 3.3 `YearMonth.kt`

**Problème rencontré** : deux API Java pures, indisponibles sur Kotlin/Native :
`java.util.Calendar` (calcul de mois précédent/suivant, détection du mois d'un timestamp) et
`java.text.SimpleDateFormat` + `java.util.Locale` (libellé « Août 2026 »).

**Choix technique** :
- `java.util.Calendar` → `kotlinx-datetime` (`Clock.System.now()`, `Instant`, `TimeZone`,
  `toLocalDateTime()`), seule bibliothèque date/heure disponible sur toutes les cibles Kotlin.
- `SimpleDateFormat(..., Locale.FRENCH)` → une simple table Kotlin
  `listOf("Janvier", "Février", ...)`. `kotlinx-datetime` ne fournit **volontairement** aucune
  API de localisation (elle dépend de la plateforme et casserait la portabilité) ; encoder les
  douze noms de mois en dur est donc le choix le plus simple et le plus robuste ici, plutôt que
  d'ajouter une dépendance supplémentaire pour un besoin aussi restreint.

**Justification** : `kotlinx-datetime` est la bibliothèque de référence recommandée par
JetBrains pour toute manipulation de date en `commonMain`. Le comportement (index de mois 0-11,
gestion du passage d'année) a été conservé à l'identique pour ne rien casser côté ViewModel/UI.
Un test de non-régression (`shared/src/commonTest/.../YearMonthTest.kt`) vérifie ce comportement.

---

## 4. Migration des couches Données et Présentation

### 4.1 `TransactionRepository.kt`

**Problème rencontré** : aucun — l'interface ne dépendait que de `kotlinx.coroutines.flow.Flow`.

**Choix technique / justification** : déplacement à l'identique.

### 4.2 `FakeTransactionRepository.kt`

**Problème rencontré** : `java.util.Calendar` (génération des dates du jeu de données factice)
et `java.util.UUID` (génération des identifiants), tous deux indisponibles en `commonMain`.

**Choix technique** :
- `Calendar` → même remplacement par `kotlinx-datetime` que pour `YearMonth` (construction d'un
  `LocalDateTime` puis conversion en `Instant`/millisecondes via `toInstant(TimeZone...)`).
- `UUID.randomUUID()` → `kotlin.uuid.Uuid.random()`, l'API d'UUID intégrée à la bibliothèque
  standard Kotlin depuis la 2.0 (encore marquée `@ExperimentalUuidApi`, d'où l'annotation
  `@OptIn` sur la classe).

**Justification** : éviter d'ajouter une dépendance tierce (ex. bibliothèque UUID externe)
alors que la bibliothèque standard Kotlin couvre déjà ce besoin nativement sur toutes les
cibles.

### 4.3 `EcoBudgetUiState` + `EcoBudgetViewModel.kt`

**Problème rencontré** : `androidx.lifecycle.ViewModel` et son `viewModelScope` sont fournis
par la bibliothèque `androidx.lifecycle:lifecycle-viewmodel-ktx`, qui ne cible que la JVM/Android
et n'est donc pas utilisable en `commonMain`. `EcoBudgetViewModel` utilisait par ailleurs
`java.util.Calendar`/`UUID` dans `saveTransaction(...)`.

**Choix technique** : remplacement de la dépendance Gradle par
`org.jetbrains.androidx.lifecycle:lifecycle-viewmodel`, un fork multiplateforme maintenu par
JetBrains qui **réexpose les mêmes classes sous le même nom de package `androidx.lifecycle`**.
Conséquence directe : **aucune ligne de code de la classe `EcoBudgetViewModel` n'a dû être
modifiée pour l'API ViewModel elle-même** (`class EcoBudgetViewModel(...) : ViewModel()`,
`viewModelScope.launch { ... }` restent identiques au caractère près) — seule la coordonnée
Gradle a changé. Les usages de `Calendar`/`UUID` ont été remplacés comme au §4.2.

**Justification** : c'est le point qui aurait pu être le plus coûteux de la migration (un
ViewModel est intrinsèquement lié au cycle de vie Android) ; le fork JetBrains supprime ce
risque en gardant une API strictement identique. Sur la cible Android, cette bibliothèque
délègue en interne à la véritable classe Google `androidx.lifecycle.ViewModel`, donc
`MainActivity.kt` continue de fonctionner sans changement (`by viewModels()` reste valide).

---

## 5. Centralisation des ressources textuelles

**Problème rencontré** : `app/src/main/res/values/strings.xml` est un fichier de ressources
Android natif, invisible depuis `commonMain`. Or `Category` (§3.1) et tous les écrans Compose
doivent pouvoir résoudre ces libellés depuis le code partagé.

**Choix technique** : mise en place de **Compose Multiplatform Resources**
(`compose.components.resources`) : tous les libellés ont été déplacés dans
`shared/src/commonMain/composeResources/values/strings.xml`. Le plugin génère une classe
`Res` (package `com.example.shared.resources`) exposant chaque entrée
(`Res.string.dialog_title_new`, etc.), résolue dans les Composables via
`org.jetbrains.compose.resources.stringResource(...)` — utilisable aussi bien depuis `:shared`
que depuis `:app`.

**Exception documentée — `app_name`** : `AndroidManifest.xml` référence
`android:label="@string/app_name"` sur `<application>` et `<activity>`. Le système de ressources
natif Android (exploité par le `PackageManager` pour le nom affiché de l'app, l'icône, etc.) ne
peut **pas** lire une ressource Compose Multiplatform générée en Kotlin : ces deux systèmes de
ressources sont indépendants. `app/src/main/res/values/strings.xml` a donc été conservé, mais
réduit à cette seule entrée `app_name` (nécessaire uniquement pour le manifeste) ; sa version
« affichage à l'écran » (l'en-tête de `EcoBudgetScreen`) est lue depuis `Res.string.app_name`
côté partagé. C'est la seule duplication volontaire de tout le projet, et elle est incontournable
avec l'outillage Android actuel.

**Justification** : Compose Multiplatform Resources est la solution officielle recommandée par
JetBrains pour ce cas d'usage exact (catalogue de chaînes partagé entre cibles). Elle conserve
la syntaxe de formatage Android (`%1$s`, `%2$d`) sans réécrire les 27 chaînes de l'application.

---

## 6. Adaptation du module `app` (Android)

Aucun fichier d'interface (`EcoBudgetScreen.kt`, `AddTransactionDialog.kt`,
`MonthNavigatorBar.kt`, `TransactionCard.kt`, `MainActivity.kt`) n'a vu sa **logique** modifiée.
Seuls les imports ont changé :

| Avant | Après |
|---|---|
| `import com.example.model.Category` | `import com.example.shared.model.Category` |
| `import com.example.viewmodel.EcoBudgetViewModel` | `import com.example.shared.presentation.EcoBudgetViewModel` |
| `import androidx.compose.ui.res.stringResource` + `com.example.R` | `import org.jetbrains.compose.resources.stringResource` + `com.example.shared.resources.Res` |
| `stringResource(R.string.xxx)` | `stringResource(Res.string.xxx)` |
| `category.labelResId` | `category.labelRes` |

`app/build.gradle.kts` ajoute `implementation(project(":shared"))` et
`implementation(compose.components.resources)` (nécessite le plugin `org.jetbrains.compose`,
ajouté uniquement pour cette résolution de ressources — l'app reste un module Android natif,
elle n'utilise pas les composants UI de Compose Multiplatform).

`TransactionCard.kt` conserve `java.text.SimpleDateFormat`/`java.util.Locale` dans sa fonction
utilitaire `formatRelativeDate(...)` : c'est un choix délibéré, ce fichier vit dans `app`
(Android uniquement, jamais compilé pour iOS), donc rien n'imposait de le faire migrer vers
`kotlinx-datetime`.

---

## 7. Validation fonctionnelle

1. Ouvrir le projet dans Android Studio (Ladybug/Meerkat ou plus récent recommandé pour un
   support KMP correct) et laisser Gradle synchroniser — la première synchro peut être longue
   (téléchargement des artefacts iOS/Compose Multiplatform).
2. Lancer la configuration `app` sur un émulateur Android (API 24+).
3. Vérifier l'absence de régression : navigation mensuelle, ajout/édition/suppression d'une
   dépense, filtre multi-catégories, calcul du budget restant, tableau de bord.
4. Lancer les tests : `./gradlew :shared:testDebugUnitTest` (tests `commonTest`, dont
   `YearMonthTest`) et `./gradlew :app:testDebugUnitTest` (Robolectric — `ExampleRobolectricTest`
   vérifie justement que `R.string.app_name` reste lisible côté Android natif après migration).

**Suggestion de découpage des commits** (pour l'historique demandé par la consigne) : un commit
par section de ce document (1. infra partagée → 2. modèles → 3. données/ViewModel → 4.
ressources → 5. adaptation UI) donne un historique lisible qui reflète l'avancement réel.

---

## 8. Points de vigilance

Cet environnement de développement ne dispose pas du SDK Android ni d'un accès aux dépôts Maven
Google/Central : le code a donc été écrit et relu avec soin, mais **n'a pas pu être compilé ici**.
Au premier `Gradle Sync` dans Android Studio, si une incompatibilité de version apparaît
(Compose Multiplatform, `lifecycle-viewmodel`, `kotlinx-datetime`), c'est très probablement un
numéro de version à ajuster dans `gradle/libs.versions.toml` — n'hésite pas à renvoyer le
message d'erreur complet.
