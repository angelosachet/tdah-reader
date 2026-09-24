# Frase a Frase (tdah-reader)

Leitor de livros em PDF para Android que mostra **uma frase de cada vez**, pensado para quem tem TDAH e se perde em páginas cheias de texto.

Você abre o PDF, o app extrai e limpa o texto, e o livro passa a ser lido frase por frase, em letra grande, sem cabeçalhos, números de página ou notas de rodapé atrapalhando.

## Instalar

1. Baixe o `frase-a-frase-<versão>.apk` na página de [Releases](../../releases) (faça login no GitHub no celular, porque o repositório é privado).
2. Abra o arquivo no celular. Se o Android pedir, permita "Instalar apps desconhecidos" para o navegador ou o gerenciador de arquivos.
3. Requer Android 8.0 ou mais novo.

## Como usar

- **Abrir PDF**: toque em *Abrir PDF* na estante ou, em qualquer app (Arquivos, Drive, WhatsApp…), use *Abrir com* ou *Compartilhar* → *Frase a Frase*.
- Na primeira vez o livro é preparado (alguns segundos). Depois ele abre na hora.
- **Avançar**: toque na parte direita da tela, deslize para a esquerda ou use o botão ›.
- **Voltar**: toque no terço esquerdo da tela, deslize para a direita ou use o botão ‹.
- O ponto de leitura é salvo automaticamente. Ao abrir o livro de novo, você continua de onde parou.
- **⋮ → Capítulos**: lista os capítulos (pelo sumário do PDF ou pelos títulos detectados). **⋮ → Ir para página**: pula para uma página do PDF original.
- **▶ Leitura automática**: as frases avançam sozinhas na velocidade escolhida.

### Ajustes (ícone **Tт**)

- Tema: Sistema, Claro, Sépia, Escuro, Preto
- Fonte: Lexend, Atkinson Hyperlegible (feitas para facilitar a leitura), sem serifa, com serifa
- Tamanho do texto e espaço entre linhas
- **Máximo de palavras por tela**: frases maiores são divididas nas vírgulas, pontos e vírgulas e travessões
- Mostrar a frase anterior em cinza (para manter o fio da meada)
- Leitura biônica (negrito no começo das palavras)
- Virar a frase com os botões de volume (e controles Bluetooth de virar página)
- Velocidade da leitura automática

## Limitações

- **PDFs escaneados** (só imagens, sem texto selecionável) não são suportados; o app avisa quando é o caso.
- PDFs com senha ou com DRM não abrem.
- A limpeza do layout usa heurísticas. Funciona bem com livros comuns, mas layouts muito elaborados (várias colunas, muitas tabelas) podem sair imperfeitos.

## Privacidade

Tudo é processado no próprio aparelho. Nenhum arquivo ou texto é enviado para a internet, e o app não pede nenhuma permissão.

## Desenvolvimento

Kotlin + Jetpack Compose (Material 3), PdfBox-Android para extrair o texto.

```
app/src/main/java/io/github/angelosachet/tdahreader/
├── text/      # Kotlin puro: montagem de linhas, limpeza de layout, hifenização, frases, trechos
├── pdf/       # extração com PdfBox-Android (linhas + posição + tamanho de fonte, sumário)
├── data/      # estante (JSON), importação de PDFs, preferências (DataStore)
└── ui/        # telas: estante, leitura, ajustes
```

Como o processamento de texto funciona:

1. `PdfTextExtractor` lê cada linha do PDF com posição, tamanho e peso da fonte.
2. `LayoutAnalyzer` remove cabeçalhos e rodapés repetidos, números de página e notas de rodapé, une as linhas em parágrafos (pelo recuo, espaçamento e linhas curtas) e desfaz a hifenização sem estragar compostos e ênclises ("guarda-chuva", "disse-lhe"), usando o vocabulário do próprio livro.
3. `SentenceSplitter` divide em frases, respeitando abreviações (Sr., Dra., pág.…), iniciais e diálogos com travessão.
4. `Chunker` quebra as frases longas no melhor ponto de pausa, dentro do limite de palavras escolhido.

### Build

Requer JDK 17 e o Android SDK (compileSdk 36).

```bash
./gradlew testDebugUnitTest   # testes do processamento de texto
./gradlew assembleDebug       # APK de debug
./gradlew assembleRelease     # APK de release (assinado se existir keystore.properties)
```

Para assinar o release, crie `keystore.properties` na raiz (fora do git):

```properties
storeFile=keystore/release.jks
storePassword=...
keyAlias=tdahreader
keyPassword=...
```

> Guarde o keystore em um lugar seguro. Para atualizar o app já instalado, o novo APK precisa ser assinado com a **mesma** chave.

Para testar a extração com PDFs reais no computador (sem celular):

```bash
TDAH_PDF_DIR=/pasta/com/pdfs ./gradlew testDebugUnitTest --tests '*RealPdfHarness*'
```

Isso gera, ao lado de cada PDF, arquivos `.paragraphs.txt`, `.chunks.txt` e `.outline.txt` para inspeção.

### Ligaduras ("Th", "ff", "fi", "ft"...)

Muitas fontes de livro desenham essas combinações como um único glifo. Quando o PDF não diz qual texto o glifo representa, ele aparece como símbolo. O app resolve em duas etapas: pelo nome do glifo na fonte (`T_h`, `f_f_i`) e, se não houver nome útil, testando as ligaduras possíveis e escolhendo a que forma palavras reais, usando o vocabulário do livro e as listas de palavras em `app/src/main/assets/words/`.

## Licenças de terceiros

- [PdfBox-Android](https://github.com/TomRoush/PdfBox-Android): Apache License 2.0
- Listas de palavras em `assets/words/`: geradas a partir de livros de domínio público do [Projeto Gutenberg](https://www.gutenberg.org)
- Fontes [Lexend](https://www.lexend.com/) e [Atkinson Hyperlegible](https://www.brailleinstitute.org/freefont/): SIL Open Font License 1.1 (textos em `app/src/main/assets/licenses/`)
