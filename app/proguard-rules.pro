# Regras específicas do Trakr para R8 em builds release.

# Room: o KSP gera o código, mas mantemos as entidades caso alguma
# referência via reflexão surja em versões futuras do compilador.
-keep class app.trakr.model.** { *; }

# ViewModels criados por factory própria (viewModelFactory): os constructors
# são chamados por reflexão via reflection do androidx.lifecycle.
-keepclassmembers class app.trakr.ui.**ViewModel {
    <init>(...);
}