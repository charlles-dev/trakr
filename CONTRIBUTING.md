# Contribuindo para o Trakr

Obrigado pelo interesse em contribuir! 🧰 O Trakr é um projeto open-source construído por e para engenheiros, makers e desenvolvedores de soluções offline. Toda contribuição é bem-vinda: código, documentação, testes de impressão 3D, correções de wiring e feedback de campo.

## TL;DR

1. Abra uma [issue](https://github.com/charlles-dev/trakrissues) explicando o problema/melhoria (ou pegue uma existente).
2. Faça um *fork* e crie uma branch descritiva (`feat/`, `fix/`, `docs/`, `chore/`).
3. Implemente seguindo as convenções abaixo.
4. Abra um **Pull Request** para `main`, descrevendo o que mudou e como testar.

## Boas-vindas a contribuidores novos

Procurando onde começar? Issues marcadas como `good first issue` são ideais. Atrasos na validade do estado "Em Desenvolvimento" são normais: o projeto está em evolução constante.

## Como rodar o projeto localmente

### Firmware (ESP32)

1. Instale o [VS Code](https://code.visualstudio.com/) + [PlatformIO IDE](https://platformio.org/install/ide?install=vscode).
2. Abra a pasta `firmware/` no VS Code.
3. Compile:
   ```shell
   pio run
   ```
4. Para testar sem o leitor YRM100, use o ambiente alternativo:
   ```shell
   pio run -e esp32radar-sim
   ```
5. Documentado em [docs/firmware/README.md](./docs/firmware/README.md).

### App Android (Kotlin)

1. Abra a pasta `app/` no Android Studio (Iguana ou superior).
2. Execute em um celular físico com Android 9+ (BLE não funciona bem em emulador).
3. Guia completo em [docs/app/README.md](./docs/app/README.md).

### Hardware / CAD

- Pinagem centralizada em `firmware/include/pins.h` — **nenhum novo pino mágico** fora deste arquivo.
- Alterações de hardware devem atualizar o BoM (`hardware/bom/bom.csv`) e o guia de wiring (`docs/hardware/README.md`).

## Convenções de código

- **C++ (firmware):** código simples para ESP32 Arduino; bibliotecas internas em `firmware/lib/Trak*` com `namespace`/prefixo do módulo; comentários registrando o porquê, não o quê.
- **Kotlin (app):** Kotlin idiomático, Jetpack Compose para UI, ViewModel por tela; cache Room é espelho local — **o firmware é a fonte da verdade**.
- **Python (CAD):** script paramétrico gerado para Fusion 360; sem dependências externas além do `adsk` da API do Fusion.

### Commits (Conventional Commits)

Seguimos o padrão [Conventional Commits](https://www.conventionalcommits.org/): `feat:`, `fix:`, `docs:`, `refactor:`, `chore:` — com escopo opcional (`feat(app):`, `feat(firmware):`).

Exemplos:
- `feat(firmware): controle de energia do YRM100`
- `docs: corrigir link do guia de wiring`
- `fix(app): crash ao reconectar BLE`

### Testes

- App: testes unitários em `app/src/test` (Parser); validação via `./gradlew assembleDebug` + testes manuais com celular físico (emulador não suporta BLE nativo). Pull requests com testes para lógica nova são bem-vindos.
- Firmware: estrutura de teste (`pio test`) ainda a ser implementada; validações manuais via `-DTRAKR_SIM` são aceitas como evidência.

## Reportando bugs

Use o modelo de issue do GitHub com: contexto, comportamento esperado, o que aconteceu, e — para hardware — fotos/vídeo se possível. Para firmware, inclua o serial log (`monitor_speed = 115200`).

## Nota sobre datasheets

Antes de mudar comandos do protocolo UHF (YRM100), valide sempre com o **datasheet** do seu lote/versão e guarde o PDF em `hardware/datasheets/`. Variantes do módulo podem ter protocolo ligeiramente diferente.

## Licença

Ao contribuir, você concorda que sua contribuição será licenciada sob a mesma licença MIT do projeto (veja `LICENSE`).