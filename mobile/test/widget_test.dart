import 'package:flutter_test/flutter_test.dart';
import 'package:physlive_mobile/main.dart';

void main() {
  testWidgets('shows PhysLive login', (tester) async {
    await tester.pumpWidget(const PhysLiveApp());
    expect(find.text('PhysLive'), findsOneWidget);
    expect(find.text('Đăng nhập'), findsOneWidget);
  });
}
