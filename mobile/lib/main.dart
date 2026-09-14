import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'api_client.dart';

void main() => runApp(const PhysLiveApp());

class PhysLiveApp extends StatelessWidget {
  const PhysLiveApp({super.key});
  @override
  Widget build(BuildContext context) => MaterialApp(
        debugShowCheckedModeBanner: false,
        title: 'PhysLive',
        theme: ThemeData(colorScheme: ColorScheme.fromSeed(seedColor: const Color(0xff3155d6)), useMaterial3: true),
        home: const LoginPage(),
      );
}

class LoginPage extends StatefulWidget {
  const LoginPage({super.key});
  @override State<LoginPage> createState() => _LoginPageState();
}

class _LoginPageState extends State<LoginPage> {
  final api = ApiClient(); final email = TextEditingController(); final password = TextEditingController();
  String error = ''; bool busy = false;
  Future<void> submit() async {
    setState(() { busy = true; error = ''; });
    try {
      final result = await api.login(email.text.trim(), password.text);
      await api.saveToken(result['token'] as String);
      if (mounted) Navigator.pushReplacement(context, MaterialPageRoute(builder: (_) => const WorkspacePage()));
    } on DioException catch (exception) {
      if (mounted) setState(() => error = exception.response?.data?['message']?.toString() ?? 'Đăng nhập thất bại');
    } finally { if (mounted) setState(() => busy = false); }
  }
  @override
  Widget build(BuildContext context) => Scaffold(
        body: Center(child: ConstrainedBox(constraints: const BoxConstraints(maxWidth: 420), child: Card(margin: const EdgeInsets.all(24), child: Padding(padding: const EdgeInsets.all(24), child: Column(mainAxisSize: MainAxisSize.min, crossAxisAlignment: CrossAxisAlignment.stretch, children: [
          const Text('PhysLive', style: TextStyle(fontSize: 32, fontWeight: FontWeight.bold)), const Text('Classroom physics companion'), const SizedBox(height: 24),
          TextField(controller: email, decoration: const InputDecoration(labelText: 'Email')), TextField(controller: password, obscureText: true, decoration: const InputDecoration(labelText: 'Mật khẩu')),
          if (error.isNotEmpty) Padding(padding: const EdgeInsets.only(top: 12), child: Text(error, style: const TextStyle(color: Colors.red))), const SizedBox(height: 18),
          FilledButton(onPressed: busy ? null : submit, child: Text(busy ? 'Đang đăng nhập…' : 'Đăng nhập')),
        ]))))),
      );
}

class WorkspacePage extends StatefulWidget {
  const WorkspacePage({super.key});
  @override State<WorkspacePage> createState() => _WorkspacePageState();
}

class _WorkspacePageState extends State<WorkspacePage> {
  final api = ApiClient(); final text = TextEditingController(text: 'Một xe chuyển động với vận tốc 10 m/s trong 5 s.');
  Map<String, dynamic>? problem; Map<String, dynamic>? simulation; String error = ''; bool busy = false;
  Future<void> understand() async {
    setState(() { busy = true; error = ''; });
    try { final created = await api.createProblem(text.text); final extracted = await api.extract(created['id'].toString()); if (mounted) setState(() => problem = extracted); }
    on DioException catch (exception) { if (mounted) setState(() => error = exception.response?.data?['message']?.toString() ?? 'Không thể hiểu đề'); }
    finally { if (mounted) setState(() => busy = false); }
  }
  Future<void> run() async {
    final spec = problem?['currentSpecification'] as Map<String, dynamic>?; if (spec?['id'] == null) return; setState(() => busy = true);
    try { final ready = await api.confirm(problem!['id'].toString(), {}); final readySpec = ready['currentSpecification'] as Map<String, dynamic>; final result = await api.simulate(readySpec['id'].toString(), 'kinematics_projectile'); if (mounted) setState(() { problem = ready; simulation = result; }); }
    on DioException catch (exception) { if (mounted) setState(() => error = exception.response?.data?['message']?.toString() ?? 'Simulation bị lỗi'); }
    finally { if (mounted) setState(() => busy = false); }
  }
  @override
  Widget build(BuildContext context) {
    final spec = problem?['currentSpecification'] as Map<String, dynamic>?;
    return Scaffold(appBar: AppBar(title: const Text('PhysLive')), body: ListView(padding: const EdgeInsets.all(16), children: [
      const Text('Teacher / Student workspace', style: TextStyle(fontSize: 24, fontWeight: FontWeight.bold)), const SizedBox(height: 16),
      TextField(controller: text, maxLines: 5, decoration: const InputDecoration(labelText: 'Đề bài', border: OutlineInputBorder())), const SizedBox(height: 12),
      FilledButton(onPressed: busy ? null : understand, child: Text(busy ? 'Đang xử lý…' : 'Hiểu đề bài')),
      if (spec != null) Card(child: Padding(padding: const EdgeInsets.all(16), child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [Text('Chủ đề: ${spec['topic'] ?? 'chưa rõ'}'), Text('Confidence: ${spec['confidence'] ?? 0}'), const SizedBox(height: 8), const Text('Tôi đã kiểm tra specification và cho phép chạy:'), FilledButton(onPressed: busy ? null : run, child: const Text('Xác nhận & chạy'))]))),
      if (simulation != null) Card(child: Padding(padding: const EdgeInsets.all(16), child: Text('Validation: ${simulation!['valid']} · ${(simulation!['time'] as List).length} mốc dữ liệu'))),
      if (error.isNotEmpty) Text(error, style: const TextStyle(color: Colors.red)),
    ]));
  }
}
