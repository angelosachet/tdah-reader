# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Projeto

"Frase a Frase": app Android (Kotlin + Jetpack Compose/Material 3, módulo único `:app`) que importa um PDF, extrai e limpa o texto e o mostra uma frase por vez, pensado para quem tem TDAH. Tudo roda no aparelho, sem internet e sem permissões. O `README.md` é a documentação para o usuário final (instalação, uso e limitações).

## Comandos

Requer JDK 17 e o Android SDK (compileSdk 36).

```bash
./gradlew testDebugUnitTest                                   # todos os testes (JVM)
./gradlew testDebugUnitTest --tests '*ChunkerTest'            # uma classe
./gradlew testDebugUnitTest --tests '*ChunkerTest*pontuacao*' # um teste (nomes com crase e espaços: use curingas)
./gradlew assembleDebug
./gradlew assembleRelease   # R8 + shrink; assinado se existir keystore.properties
./gradlew lint              # Android Lint (não há ktlint/detekt configurado)
```

Harness manual com PDFs reais (pulado quando a variável não existe):

```bash
TDAH_PDF_DIR=/pasta/com/pdfs ./gradlew testDebugUnitTest --tests '*RealPdfHarness*'
# TDAH_DUMP_PAGES=3,10 imprime as linhas cruas dessas páginas (1-based) com y, x0..x1, fonte e negrito
```

Ele grava `.paragraphs.txt`, `.chunks.txt` e `.outline.txt` ao lado de cada PDF. É a forma de depurar heurísticas de layout sem celular.

## Arquitetura

### Pipeline de importação (`data/BookImporter.kt`, em `Dispatchers.IO`)

1. Copia o PDF para o cache calculando SHA-256; o id do livro são os 12 primeiros bytes em hex. **O PDF é descartado depois**: só o texto processado fica salvo (`filesDir/library.json` e `filesDir/books/<id>.json`).
2. `pdf/PdfTextExtractor` (subclasse de `PDFTextStripper`) emite um `Glyph` por caractere, com posição, fonte e negrito. Ligaduras sem Unicode são recuperadas aqui pelo nome do glifo na fonte.
3. `text/PageModel.kt` (`LineAssembler`) monta `TextLine`s: tamanho de fonte dominante, baseline mediana e remoção de chamadas de nota sobrescritas (marca `leadingMarker`).
4. `text/LayoutAnalyzer.analyze`, nesta ordem: `GlyphRepair.repair` (adivinha ligaduras que sobraram usando o vocabulário do livro e, só se preciso, as listas de `assets/words/`) → limpeza de linha e acentos combinantes → tamanho de fonte do corpo → remoção de cabeçalhos e rodapés repetidos (assinatura sem dígitos nas margens) → notas de rodapé → `ParagraphBuilder` + `Dehyphenator` → junção de títulos com letras espaçadas.
5. Resultado: `BookContent(paragraphs, outline, formatVersion)`.
6. `data/CoverRenderer` desenha a capa com o `PdfRenderer` do Android (primeira página não branca entre as 3 primeiras) em `filesDir/covers/<id>.jpg`. Ele só roda durante a importação, enquanto o PDF temporário existe. Sem arquivo de capa, a UI desenha uma capa ilustrada (`ui/library/BookCover.kt`). Uma capa escolhida da galeria nunca é sobrescrita.

### Leitura (`ui/reader/ReaderViewModel.kt`)

A divisão em trechos **não é salva**: `Chunker(maxWords)` → `SentenceSplitter` roda ao abrir o livro e de novo quando `maxWords` muda nos ajustes.

### Gamificação

- `game/Progression.kt` é Kotlin puro (testado em `ProgressionTest`): `ReadingStats` serializável, `Levels`, o enum `Achievement` e funções `Progression.*` que recebem o progresso e devolvem `Update(stats, events)`.
- `data/StatsRepository` grava `filesDir/stats.json`, aplica as mudanças em ordem por um `Channel` (só depois de carregar o arquivo) e emite `GameEvent`s num `SharedFlow`. O `CelebrationHost` (em `AppRoot`, por cima de todas as telas) mostra os avisos e o confete.
- Quem alimenta é o `ReaderViewModel.next()`: conta o trecho que sai da tela só se ele estiver depois de `readFrontier` e ficou na tela pelo menos 300 ms. O tempo por trecho é limitado a 60 s. Capítulo concluído = cruzar para outra entrada de `chapters` com pelo menos 20 trechos.
- Conquista nova: valor no enum (alvo + métrica), mais `icon`/`titleRes`/`descriptionRes` em `ui/game/GameUi.kt` e as strings `ach_*`. As conquistas são guardadas pelo `name` do enum, então não renomeie valores existentes.
- Em pt-BR o 0 cai na forma "one" dos plurais ("0 dia"); use `countText()` em vez de `pluralStringResource` direto.

### Invariantes que atravessam arquivos

- **Posição de leitura = deslocamento global em caracteres**: soma de `paragraph.text.length + 1` por parágrafo. `Chunker` (`Chunk.start`) e `BookImporter` (`totalChars`) precisam calcular igual. `BookEntry.position` guarda esse valor, e `indexFor` faz busca binária para achar o trecho, o que mantém o lugar mesmo mudando `maxWords`. Mudar essa conta invalida o progresso salvo de todos os livros.
- **Páginas**: `Paragraph.page` + `pageMarks` (offsets dentro do parágrafo) → `Chunk.page`. Isso alimenta "Ir para página" e o mapeamento do sumário do PDF para capítulos.
- **`BookContent.FORMAT_VERSION`** (`data/Models.kt`): incremente quando a saída do pipeline mudar. Com isso, reabrir o mesmo PDF reprocessa o livro (mantendo o progresso proporcional) em vez de usar o cache. Livros já importados **não** são reprocessados sozinhos, porque o PDF não fica guardado.
- **`text/` e `game/` são Kotlin puro** (sem Android e sem biblioteca de PDF) para rodar nos testes JVM. Não importe Android nem PdfBox ali.
- **`app/src/test/.../pdf/JvmPdfTextExtractor.kt` é uma cópia manual de `PdfTextExtractor`** usando Apache PDFBox 2.0.27 (o mesmo motor do PdfBox-Android 2.0.27.0). Toda mudança no extrator precisa ser replicada lá.

### Montagem do app

- Injeção manual: `AppContainer` (criado em `TdahReaderApp`, que também chama `PDFBoxResourceLoader.init`) com um único `CoroutineScope` do app. O ViewModel é criado com `viewModel { ReaderViewModel(...) }`.
- Navegação tipada em `ui/AppRoot.kt` (`LibraryRoute`, `ReaderRoute(bookId)`, `StatsRoute`). `ImportState` dirige a navegação: `Done` abre o leitor; um PDF que chega por intent fora da estante volta para ela.
- Teclas físicas: `MainActivity.dispatchKeyEvent` → `HardwareKeyRouter` → handler instalado pela `ReaderScreen` via `DisposableEffect`.
- `MainActivity` trata a rotação sozinha (`configChanges` no manifest) e não é recriada. É `singleTask` e recebe PDFs por `VIEW`/`SEND`.
- `LibraryRepository`: `StateFlow` em memória + gravação atômica (tmp + rename). Posição gravada com debounce de 800 ms e `flush()` em `onStop`/`onCleared`. Só um `BookContent` fica em cache. Capas ficam num `LruCache`, e `coverRevision` muda a cada capa salva ou apagada para as telas recarregarem.
- Ajustes (DataStore): uma opção nova mexe em `ReaderSettings` (padrão e limites MIN/MAX), `Keys`, `update()`, `toSettings()` (com `coerceIn`), `ReaderSettingsSheet` e `strings.xml`. `settings.current` é `null` até a primeira leitura, e o `AppRoot` espera por ele para não piscar o tema errado.
- Cores do leitor vêm de `LocalReaderPalette` (`ui/theme/Theme.kt`), não direto do `MaterialTheme`.

## Convenções

- Tudo em português: textos da UI (só existe `values/strings.xml`), comentários, KDoc, nomes de teste (com crase) e mensagens de commit. Identificadores em inglês.
- R8 está ligado no release, e o PdfBox usa reflexão (regras em `proguard-rules.pro`). Ao adicionar uma biblioteca, confira com `assembleRelease`. O BouncyCastle é excluído de propósito.

## Release

1. Incremente `versionCode` e `versionName` em `app/build.gradle.kts`.
2. `./gradlew assembleRelease` e renomeie `app/build/outputs/apk/release/app-release.apk` para `frase-a-frase-<versionName>.apk`.
3. `gh release create v<versionName>` com o título `Frase a Frase <versionName>` (repositório privado `angelosachet/tdah-reader`). As notas são em português para o usuário final e lembram de instalar por cima da versão anterior.

A assinatura vem de `keystore.properties` + `keystore/release.jks` (fora do git, só nesta máquina). Nunca gere uma chave nova, porque o Android recusa atualizar um app assinado com outra chave. Sem `keystore.properties`, o release cai na chave de debug e não instala por cima da versão publicada.
