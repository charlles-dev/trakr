import 'package:flutter/material.dart';

void main() {
  runApp(const TrakrApp());
}

class TrakrApp extends StatelessWidget {
  const TrakrApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Trakr',
      theme: ThemeData(
        colorScheme: ColorScheme.fromSeed(seedColor: const Color(0xFF2F6F4E)),
        useMaterial3: true,
      ),
      home: const DashboardScreen(),
    );
  }
}

class DashboardScreen extends StatelessWidget {
  const DashboardScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Trakr')),
      body: const Center(child: Text('Conecte sua maleta por BLE')),
    );
  }
}