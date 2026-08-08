/// Modelos de dados do Trakr: [Toolbox] e [Tool].
library;

class Toolbox {
  const Toolbox({required this.id, required this.name, this.tools = const []});

  final String id;
  final String name;
  final List<Tool> tools;
}

class Tool {
  const Tool({
    required this.id,
    required this.name,
    this.icon = 'wrench',
    this.present = true,
  });

  final String id;
  final String name;
  final String icon;
  final bool present;
}
