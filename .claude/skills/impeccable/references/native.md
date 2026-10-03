# App native (Compose, SwiftUI, Flutter, React Native)

## Vederla
- Senza emulatore o simulatore nell'ambiente non si vede nulla: ragiona sul codice e dichiaralo.
- Compose: le `@Preview` (anche con `uiMode = UI_MODE_NIGHT_YES`, `fontScale = 1.5f`,
  `widthDp` diverse) sono il modo più rapido per controllare temi e dimensioni; se il progetto
  usa test di screenshot (Paparazzi, Roborazzi) sfruttali.
- SwiftUI: `#Preview` con `.environment(\.colorScheme, .dark)`, `\.dynamicTypeSize`,
  `\.layoutDirection`.
- Flutter: golden test, se il progetto li ha.

## Jetpack Compose / Compose Multiplatform
- Colori da `MaterialTheme.colorScheme` o dall'oggetto tema del progetto; `Color(0x…)` in una
  schermata è quasi sempre un token mancante: `rg -n 'Color\(0x' --glob '*.kt'`.
- Testo da `MaterialTheme.typography`; `fontSize = NN.sp` sparsi vanno ricondotti alla scala.
- Spaziature e raggi dalle costanti del progetto, non `13.dp` casuali.
- Accessibilità: `contentDescription` (null per le decorative), `Modifier.semantics` /
  `mergeDescendants` per righe cliccabili, `Role` corretto, `minimumInteractiveComponentSize()`
  o 48.dp minimi; `clickable` con `onClickLabel` quando l'azione non è ovvia.
- Liste: `LazyColumn` con `key` e `contentType`; `contentPadding` per insets e barre.
- Insets: `WindowInsets.safeDrawing`/`ime` (o gli helper del progetto), `imePadding()` sui moduli.
- Movimento: `AnimatedVisibility`, `animate*AsState` con le specifiche di animazione del progetto;
  rispetta l'impostazione di sistema delle animazioni dove il progetto la legge.
- Testi: `KeyboardOptions` (tipo, `ImeAction`, `autoCorrect`/`capitalization`), `maxLines` +
  `overflow` dove il testo può essere lungo.

## SwiftUI
- Colori dall'asset catalog o da estensioni `Color` con varianti chiaro/scuro; tipografia con
  stili di sistema (`.font(.headline)`) che seguono Dynamic Type, non `.system(size:)` fissi.
- `accessibilityLabel`, `accessibilityHint`, `.accessibilityElement(children: .combine)`,
  `accessibilityHidden(true)` per le decorative; target ≥ 44 pt (`.contentShape` + `frame`).
- `@Environment(\.accessibilityReduceMotion)` per ridurre le animazioni.
- `safeAreaInset`, `.scrollDismissesKeyboard`, `.submitLabel` sui campi.

## Flutter
- `Theme.of(context).colorScheme` / `textTheme`, `ThemeExtension` per i token propri.
- `Semantics`, `tooltip` sulle `IconButton`, `MediaQuery.textScalerOf` rispettato,
  `MediaQuery.disableAnimationsOf` per il movimento.

## React Native
- Token in un modulo tema, `useColorScheme()` per il tema scuro.
- `accessibilityLabel`/`accessibilityRole`, `hitSlop` per target piccoli,
  `AccessibilityInfo.isReduceMotionEnabled()`, `KeyboardAvoidingView` sui moduli.

## Piattaforma
Ogni piattaforma ha le sue convenzioni (Material su Android, Human Interface Guidelines su iOS):
navigazione indietro, posizione delle azioni, fogli e dialoghi, aptica. Un'app multipiattaforma
può avere uno stile proprio, ma i gesti e i comportamenti di sistema devono restare quelli attesi.
