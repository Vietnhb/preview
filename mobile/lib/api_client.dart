import 'package:dio/dio.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';

class ApiClient {
  ApiClient() : _dio = Dio(BaseOptions(baseUrl: const String.fromEnvironment('API_BASE_URL', defaultValue: 'http://10.0.2.2:8080/api'))) {
    _dio.interceptors.add(InterceptorsWrapper(onRequest: (options, handler) async {
      final token = await _storage.read(key: 'physlive_token');
      if (token != null) options.headers['Authorization'] = 'Bearer $token';
      handler.next(options);
    }));
  }
  final Dio _dio;
  final FlutterSecureStorage _storage = const FlutterSecureStorage();
  Future<Map<String, dynamic>> login(String email, String password) async => (await _dio.post('/auth/login', data: {'email': email, 'password': password})).data as Map<String, dynamic>;
  Future<void> saveToken(String token) => _storage.write(key: 'physlive_token', value: token);
  Future<Map<String, dynamic>> createProblem(String text) async => (await _dio.post('/problems', data: {'text': text, 'sourceMode': 'TEXT'})).data as Map<String, dynamic>;
  Future<Map<String, dynamic>> extract(String id) async => (await _dio.post('/problems/$id/extract')).data as Map<String, dynamic>;
  Future<Map<String, dynamic>> confirm(String id, Map<String, String> answers) async => (await _dio.post('/problems/$id/confirm', data: {'answers': answers})).data as Map<String, dynamic>;
  Future<Map<String, dynamic>> simulate(String specificationId, String schemaId) async => (await _dio.post('/simulations', data: {'specificationId': specificationId, 'schemaId': schemaId, 'adjustableParams': {'velocity': 10.0}, 'durationSeconds': 10, 'stepSeconds': .05})).data as Map<String, dynamic>;
}
