# THIẾT KẾ HỆ THỐNG B2B - PHYSLIVE (V2 - UPDATED)

> Updated 19/09/2026: schools may self-register a SCHOOL_MANAGER account and choose a plan. Activate only after verified VNPAY Sandbox payment, then notify platform admins. Teachers/students are still provisioned by school managers. AI quota is actual provider tokens; finish and charge an in-flight call fully, then block subsequent calls when exhausted. See B2B_IMPLEMENTATION_SUMMARY.md for implemented scope and verification limits.


**Phiên bản:** 2.0 (Cập nhật dựa trên requirements thực tế)  
**Ngày:** 17/09/2026  
**Mô hình:** B2B SaaS - Mô phỏng Vật Lý THPT

---

## 📋 ĐIỂM KHÁC BIỆT CHÍNH SO VỚI V1

### ✅ CẬP NHẬT QUAN TRỌNG:

1. **AI Generation**: Giáo viên tạo mô phỏng qua AI chat (mô tả đề bài → AI tạo simulation)
2. **Quota Model**: 
   - Quota A: Số học sinh (500/1,000/5,000)
   - Quota B: AI tokens để TẠO mô phỏng (chỉ giáo viên tốn, học sinh chạy free)
3. **Thư viện**: 
   - Personal Library: Mô phỏng riêng của giáo viên
   - Shared Library: Giáo viên share → dùng miễn phí (không tốn quota)
4. **Phạm vi**: Chỉ Vật Lý THPT (lớp 10-12) - Đầy đủ chương trình
5. **MVP**: 3 mô phỏng cơ bản để pilot
6. **Auto-grading**: AI chấm theo công thức + kết quả → Giáo viên review
7. **Log & Replay**: Lưu action logs → Giáo viên xem lại quá trình học sinh làm bài

---

## 1. TỔNG QUAN HỆ THỐNG

### 1.1. Elevator Pitch

> **PhysLive B2B** - Nền tảng SaaS cho trường THPT, giúp giáo viên Vật Lý **tạo mô phỏng tương tác bằng AI** chỉ với mô tả tiếng Việt. Học sinh làm bài, AI chấm tự động, giáo viên review. Thư viện chia sẻ được kiểm duyệt chất lượng bởi team chuyên gia.

### 1.2. System Architecture Overview

```
┌─────────────────────────────────────────────────────────────┐
│                  PLATFORM LEVEL (PhysLive)                  │
│                                                             │
│  ┌──────────────┐  ┌────────────────────────────────────┐ │
│  │ SYSTEM_ADMIN │  │  CONTENT_REVIEWER (Platform Team)  │ │
│  └──────────────┘  └────────────────────────────────────┘ │
│         │                        │                         │
│         │                        │ Quality Control         │
│         ▼                        ▼                         │
│  [System Mgmt]         [Shared Library Review]            │
│                                                             │
└─────────────────────────────────────────────────────────────┘
                           │
              ┌────────────┴────────────┐
              ▼                         ▼
┌──────────────────────────┐  ┌──────────────────────────┐
│      SCHOOL A            │  │      SCHOOL B            │
│  ┌────────┐  ┌────────┐ │  │  ┌────────┐  ┌────────┐ │
│  │TEACHER │  │STUDENT │ │  │  │TEACHER │  │STUDENT │ │
│  └────────┘  └────────┘ │  │  └────────┘  └────────┘ │
└──────────────────────────┘  └──────────────────────────┘
```

### 1.3. Unique Value Propositions

| # | Giá trị | Mô tả |
|---|---------|-------|
| 1 | **AI Tạo Mô Phỏng** | Giáo viên gõ đề bài tiếng Việt → AI phân tích → Tự động tạo simulation (như ChatGPT nhưng ra mô phỏng vật lý) |
| 2 | **Theo Chương Trình VN** | Nội dung Vật Lý THPT lớp 10-12 chuẩn Bộ GD&ĐT, không cần "dịch" từ nước ngoài |
| 3 | **Thư Viện Chia Sẻ** | Giáo viên share → Mọi người dùng free → Cộng đồng phát triển nội dung |
| 4 | **Auto-Grading** | AI chấm bài tự động → Giáo viên chỉ review → Tiết kiệm 70% thời gian chấm bài |
| 5 | **Replay Học Tập** | Xem lại quá trình học sinh làm bài (như replay game) → Hiểu sai lầm ở đâu |

### 1.4. Đối Tượng Khách Hàng (ICP)

```
PRIMARY: Trường THPT (công/tư)
  - Quy mô: 500-5,000 học sinh
  - Địa bàn: Thành phố lớn (HCM, Hà Nội, Đà Nẵng, Cần Thơ)
  - Pain points:
    • Thiếu phòng thí nghiệm vật lý
    • Học sinh khó hình dung khái niệm trừu tượng
    • Giáo viên mất nhiều thời gian chấm bài
    • Không có công cụ theo dõi tiến độ học tập

SECONDARY (Giai đoạn 2):
  - Trường THCS (lớp 6-9)
  - Trung tâm luyện thi
```

---

## 2. MÔ HÌNH KINH DOANH

### 2.1. Pricing Strategy

```
┌────────────────────────────────────────────────────────────────┐
│                        GÓI STARTER                             │
├────────────────────────────────────────────────────────────────┤
│ Quota học sinh:        500 HS                                  │
│ Quota AI (tokens):     100,000 tokens/tháng                    │
│                        (~50-100 simulations/tháng)             │
│ Lớp học:               Unlimited                               │
│ Giáo viên:             Unlimited                               │
│ Storage:               50 GB                                   │
│ Simulations chạy:      Unlimited (học sinh chạy free)          │
│ Shared library:        ✅ Dùng được                            │
│ Personal library:      ✅ Có                                   │
│ Auto-grading:          ✅ Basic                                │
│ Support:               Email (48h response)                    │
│ Priority rendering:    ❌                                       │
│ Advanced analytics:    ❌                                       │
│                                                                │
│ GIÁ: 30,000,000 VNĐ/năm (~60k VNĐ/HS/năm)                     │
└────────────────────────────────────────────────────────────────┘

┌────────────────────────────────────────────────────────────────┐
│                     GÓI PROFESSIONAL                           │
├────────────────────────────────────────────────────────────────┤
│ Quota học sinh:        1,000 HS                                │
│ Quota AI (tokens):     300,000 tokens/tháng                    │
│                        (~150-300 simulations/tháng)            │
│ Lớp học:               Unlimited                               │
│ Giáo viên:             Unlimited                               │
│ Storage:               100 GB                                  │
│ Simulations chạy:      Unlimited                               │
│ Shared library:        ✅ Dùng được + Upload                   │
│ Personal library:      ✅ Có                                   │
│ Auto-grading:          ✅ Advanced                             │
│ Support:               Priority email + Chat (24h)             │
│ Priority rendering:    ✅                                       │
│ Advanced analytics:    ✅                                       │
│ Export data:           ✅ Excel/PDF                            │
│                                                                │
│ GIÁ: 50,000,000 VNĐ/năm (~50k VNĐ/HS/năm)                     │
└────────────────────────────────────────────────────────────────┘

┌────────────────────────────────────────────────────────────────┐
│                      GÓI ENTERPRISE                            │
├────────────────────────────────────────────────────────────────┤
│ Quota học sinh:        5,000 HS                                │
│ Quota AI (tokens):     UNLIMITED                               │
│ Lớp học:               Unlimited                               │
│ Giáo viên:             Unlimited                               │
│ Storage:               500 GB                                  │
│ Simulations chạy:      Unlimited                               │
│ Shared library:        ✅ Dùng được + Upload + Featured        │
│ Personal library:      ✅ Có                                   │
│ Auto-grading:          ✅ Advanced + Custom logic              │
│ Support:               24/7 Priority + Dedicated CSM           │
│ Priority rendering:    ✅                                       │
│ Advanced analytics:    ✅ + Custom reports                     │
│ Export data:           ✅ Excel/PDF/API                        │
│ White-label:           ✅ (optional)                           │
│ On-premise:            ✅ (optional, extra cost)               │
│                                                                │
│ GIÁ: 200,000,000 VNĐ/năm (~40k VNĐ/HS/năm)                    │
└────────────────────────────────────────────────────────────────┘
```

### 2.2. Revenue Model

```
Doanh thu chính: License phí hàng năm
Add-ons:
  - Mua thêm AI tokens:     500K VNĐ / 50,000 tokens
  - Priority support 1 tháng: 2M VNĐ
  - Custom integration:     Theo dự án
  - Training onsite:        5M VNĐ/ngày

Target năm đầu:
  - 60 trường Starter:      60 × 30M  = 1.8 tỷ VNĐ
  - 30 trường Professional: 30 × 50M  = 1.5 tỷ VNĐ
  - 10 trường Enterprise:   10 × 200M = 2.0 tỷ VNĐ
  ───────────────────────────────────────────────
  TỔNG:                                5.3 tỷ VNĐ
  
  + Add-ons (ước tính 10%):           0.5 tỷ VNĐ
  ═══════════════════════════════════════════════
  TỔNG REVENUE:                       5.8 tỷ VNĐ
```

### 2.3. Cost Structure (Ước tính)

```
Chi phí vận hành năm đầu:
  - Infrastructure (AWS/Supabase):  300M VNĐ
  - OpenAI API costs:               400M VNĐ
  - Team salaries (10 người):       1,200M VNĐ
  - Marketing & Sales:              500M VNĐ
  - Operations & Support:           200M VNĐ
  ────────────────────────────────────────────
  TỔNG CHI PHÍ:                     2,600M VNĐ

LỢI NHUẬN (năm đầu):               3,200M VNĐ (~55% margin)
```

---

## 3. LUỒNG NGHIỆP VỤ CHI TIẾT

### 3.1. Luồng Giáo Viên Tạo Mô Phỏng (CORE FEATURE)

```
┌───────────────────────────────────────────────────────────────┐
│         GIÁO VIÊN TẠO MÔ PHỎNG QUA AI CHAT                   │
└───────────────────────────────────────────────────────────────┘

[1] Giáo viên vào Workspace → Click "Tạo simulation"
    ↓
[2] Modal mở ra - AI Chat Interface
    Title: "Tạo mô phỏng mới"
    Subtitle: "Trao đổi với PhysLive AI để xây dựng mô phỏng."
    ↓
[3] Giáo viên nhập đề bài (text hoặc upload ảnh):
    
    VD: "Tạo mô phỏng chuyển động rơi tự do. Vật rơi từ độ cao h0=20m,
         vận tốc ban đầu v0=0, g=10m/s². Yêu cầu học sinh tính:
         1. Thời gian rơi
         2. Vận tốc khi chạm đất"
    
    HOẶC upload ảnh đề bài → OCR → AI đọc
    ↓
[4] Hệ thống:
    - Gửi đề bài đến OpenAI API
    - Tốn AI tokens (tùy độ phức tạp)
    - Cập nhật quota usage
    ↓
[5] AI phân tích đề bài:
    - Extract thông tin: vật lý, điều kiện ban đầu, yêu cầu
    - Phát hiện ambiguities (nếu có):
      "Có xét sức cản không khí không?"
      "Vật có hình dạng gì?"
    - Tạo Specification JSON:
      ```json
      {
        "objects": [
          {"id": "ball", "type": "rigid_body", "mass": 1, "shape": "sphere"}
        ],
        "quantities": [
          {"name": "h0", "value": 20, "unit": "m"},
          {"name": "v0", "value": 0, "unit": "m/s"},
          {"name": "g", "value": 10, "unit": "m/s²"}
        ],
        "relations": [
          {"type": "free_fall", "object": "ball", "equation": "h = h0 - 0.5*g*t²"}
        ]
      }
      ```
    ↓
[6] AI hỏi lại nếu có điểm chưa rõ:
    "🤖 Câu hỏi 1/2: Có xét sức cản không khí không?
     [Có xét] [Không xét] [Nhập tự do...]"
    
    Giáo viên trả lời → AI tiếp tục phân tích
    ↓
[7] AI tạo Specification hoàn chỉnh → Hiển thị preview:
    
    "📋 Specification
     
     Objects:
       • Ball (rigid body, m=1kg, r=0.05m)
     
     Initial Conditions:
       • h0 = 20m
       • v0 = 0 m/s
       • g = 10 m/s²
     
     Physics:
       • Free fall (no air resistance)
       • Equation: h(t) = 20 - 5t²
     
     [Chỉnh sửa] [Xác nhận & tạo]"
    ↓
[8] Giáo viên click "Xác nhận"
    ↓
[9] Hệ thống:
    - Compile specification → Scene graph
    - Render preview canvas
    - Validate physics
    - Lưu vào Personal Library
    - Status: DRAFT
    ↓
[10] Giáo viên xem simulation chạy thử:
     - Play/Pause
     - Adjust parameters
     - Xem kết quả
     ↓
[11] Giáo viên:
     ├─► [Lưu & Share] → Vào Shared Library (mọi trường dùng được)
     │                   → Không tốn quota AI khi người khác dùng
     │
     └─► [Chỉ lưu riêng] → Personal Library
                         → Chỉ giáo viên này dùng
     ↓
[12] Giáo viên có thể:
     - Giao bài cho học sinh
     - Clone & customize
     - Xóa/Archive
```

### 3.2. Luồng Giao Bài & Auto-Grading

```
┌───────────────────────────────────────────────────────────────┐
│              GIAO BÀI & TỰ ĐỘNG CHẤM ĐIỂM                     │
└───────────────────────────────────────────────────────────────┘

[TEACHER]

[1] Vào Library → Chọn simulation → Click "Giao bài"
    ↓
[2] Form giao bài:
    • Chọn lớp: 10A1
    • Tiêu đề: "Bài tập Chuyển động rơi tự do"
    • Mô tả: "Áp dụng công thức..."
    • Deadline: 2026-10-01 23:59
    • Điểm tối đa: 10
    • Số lần làm lại: Unlimited
    ↓
[3] Setup Grading Criteria:
    
    [Auto-grading] ✅ Bật
    
    Câu hỏi 1: Tính thời gian rơi (4 điểm)
      • Công thức đúng: h = 0.5*g*t²    (2đ)
      • Kết quả: t = 2s (±0.1s)         (2đ)
    
    Câu hỏi 2: Tính vận tốc chạm đất (6 điểm)
      • Công thức đúng: v = g*t         (3đ)
      • Kết quả: v = 20 m/s (±0.5)      (3đ)
    
    [Giáo viên duyệt sau khi AI chấm] ✅
    ↓
[4] Click "Giao bài" → Hệ thống:
    - Tạo Assignment record
    - Gửi notification cho học sinh (email + in-app)
    ↓

═══════════════════════════════════════════════════════════════

[STUDENT]

[5] Học sinh nhận thông báo → Vào "My Assignments"
    ↓
[6] Click assignment → Đọc đề → "Bắt đầu làm"
    ↓
[7] Simulation load:
    - Canvas hiển thị
    - Controls bên cạnh (play, pause, reset, adjust params)
    - Input fields cho câu trả lời
    ↓
[8] Học sinh tương tác:
    - Chạy mô phỏng
    - Quan sát kết quả
    - Điều chỉnh tham số (nếu được phép)
    - Ghi lại measurement
    ↓
[9] Học sinh nhập câu trả lời:
    
    Câu 1: Thời gian rơi
      Công thức: [text input] "h = 1/2 * g * t^2"
      Kết quả: [number input] "2" [s]
    
    Câu 2: Vận tốc chạm đất
      Công thức: [text input] "v = g * t"
      Kết quả: [number input] "20" [m/s]
    ↓
[10] Hệ thống ghi log actions (real-time):
     ```json
     {
       "student_id": "uuid",
       "assignment_id": "uuid",
       "actions": [
         {
           "timestamp": "2026-09-17T10:00:01Z",
           "action": "run_simulation",
           "params": {"h0": 20, "v0": 0, "g": 10}
         },
         {
           "timestamp": "2026-09-17T10:00:15Z",
           "action": "take_measurement",
           "measurement": {"time": 2.0, "position": 0}
         },
         {
           "timestamp": "2026-09-17T10:01:00Z",
           "action": "input_formula",
           "question": 1,
           "formula": "h = 1/2 * g * t^2"
         },
         {
           "timestamp": "2026-09-17T10:01:30Z",
           "action": "input_result",
           "question": 1,
           "value": 2,
           "unit": "s"
         }
       ]
     }
     ```
    ↓
[11] Học sinh click "Nộp bài"
     ↓
[12] Hệ thống AI Auto-grading:
     
     Câu 1:
       - Formula check: "h = 1/2 * g * t^2" ≈ "h = 0.5*g*t²" ✅ (2đ)
       - Result check: 2.0s ≈ 2s (tolerance 0.1) ✅ (2đ)
       → Total: 4/4 điểm
     
     Câu 2:
       - Formula check: "v = g * t" ✅ (3đ)
       - Result check: 20 m/s ✅ (3đ)
       → Total: 6/6 điểm
     
     ═══════════════════════════════
     AI SUGGESTED SCORE: 10/10
     Status: PENDING_REVIEW
     ↓
[13] Gửi notification cho giáo viên: "1 bài mới chờ duyệt"
     ↓

═══════════════════════════════════════════════════════════════

[TEACHER - REVIEW]

[14] Giáo viên vào "Grading Queue"
     ↓
[15] Xem submission của học sinh:
     
     [Tab 1: Kết quả]
       - AI score: 10/10
       - Câu trả lời chi tiết
       - Time spent: 7 phút
     
     [Tab 2: Replay]
       - Timeline các actions
       - Số lần chạy simulation: 3 lần
       - Có thể xem lại từng bước
     
     [Tab 3: Thống kê]
       - So với trung bình lớp
       - Điểm mạnh/yếu
     ↓
[16] Giáo viên:
     ├─► [Chấp nhận điểm AI] → Score: 10/10, Status: GRADED
     │   → Gửi notification cho học sinh
     │
     ├─► [Điều chỉnh điểm] → Score: 9.5/10
     │   → Feedback: "Công thức đúng nhưng nên làm tròn kết quả"
     │   → Status: GRADED
     │
     └─► [Yêu cầu làm lại] → Status: REVISION_REQUESTED
         → Feedback: "Em xem lại công thức câu 2"
```

---

## 4. ROLES & PERMISSIONS

### 4.1. Role Definitions

```
┌─────────────────────────────────────────────────────────────┐
│                   5 ROLES IN SYSTEM                         │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  PLATFORM LEVEL (school_id = NULL)                         │
│  ═══════════════════════════════════════                   │
│                                                             │
│  1. SYSTEM_ADMIN                                           │
│     • Quản lý toàn hệ thống                                │
│     • Duyệt trường mới đăng ký                             │
│     • Cấp/gia hạn licenses                                 │
│     • Quản lý users toàn platform                          │
│     • System monitoring & analytics                        │
│     • KHÔNG thuộc trường nào                               │
│                                                             │
│  2. CONTENT_REVIEWER                                       │
│     • Duyệt simulations trước khi vào Shared Library       │
│     • Quality control (physics accuracy)                   │
│     • Feature simulations (highlight quality content)      │
│     • Xử lý reports (vi phạm, sai sự thật)                │
│     • Quản lý Topic Schemas & Benchmarks (từ code cũ)     │
│     • KHÔNG thuộc trường nào                               │
│     • Thường là giáo viên Vật Lý giỏi/researchers          │
│                                                             │
│  ───────────────────────────────────────────────────────   │
│                                                             │
│  SCHOOL LEVEL (school_id = UUID)                           │
│  ═══════════════════════════════════                       │
│                                                             │
│  3. SCHOOL_ADMIN ⭐ NEW                                     │
│     • Thuộc 1 trường cụ thể                                │
│     • Quản lý users của trường (CRUD Teacher/Student)      │
│     • Import CSV (hàng loạt)                               │
│     • Tạo lớp học                                          │
│     • Phân giáo viên vào lớp                               │
│     • Phân học sinh vào lớp                                │
│     • Xem báo cáo trường                                   │
│     • Xem & quản lý quota                                  │
│     • KHÔNG tạo simulation / giao bài / chấm bài          │
│     • Thường là: Hiệu trưởng, Phó HT, Tổ trưởng           │
│                                                             │
│  4. TEACHER                                                │
│     • Thuộc 1 trường cụ thể                                │
│     • Tạo simulations (AI-powered)                         │
│     • Giao bài cho lớp                                     │
│     • Chấm bài (review AI auto-grading)                    │
│     • Share simulations (cần REVIEWER duyệt)               │
│     • Xem báo cáo lớp của mình                             │
│     • CÓ THỂ phân học sinh vào lớp mình dạy               │
│                                                             │
│  5. STUDENT                                                │
│     • Thuộc 1 trường cụ thể                                │
│     • Làm bài tập được giao                                │
│     • Chạy simulations (unlimited)                         │
│     • Xem điểm & tiến độ cá nhân                           │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

### 4.2. Permission Matrix

| Feature | SYS_ADMIN | REVIEWER | SCHOOL_ADMIN | TEACHER | STUDENT |
|---------|:---------:|:--------:|:------------:|:-------:|:-------:|
| **Platform Management** |
| Quản lý schools | ✅ | ❌ | ❌ | ❌ | ❌ |
| Quản lý licenses | ✅ | ❌ | ❌ | ❌ | ❌ |
| Xem toàn bộ users | ✅ | ✅ | ❌ | ❌ | ❌ |
| System analytics | ✅ | ✅ | ❌ | ❌ | ❌ |
| **School Management** |
| Tạo Teacher/Student | ✅ | ❌ | ✅ | ❌ | ❌ |
| Edit users (school) | ✅ | ❌ | ✅ | ❌ | ❌ |
| Delete users (school) | ✅ | ❌ | ✅ | ❌ | ❌ |
| Import CSV users | ✅ | ❌ | ✅ | ❌ | ❌ |
| Tạo lớp học | ✅ | ❌ | ✅ | ❌ | ❌ |
| Phân giáo viên vào lớp | ✅ | ❌ | ✅ | ❌ | ❌ |
| Phân học sinh vào lớp | ✅ | ❌ | ✅ | ✅* | ❌ |
| Xem quota trường | ✅ | ❌ | ✅ | ✅ | ❌ |
| Xem báo cáo trường | ✅ | ❌ | ✅ | ❌ | ❌ |
| **Content Moderation** |
| Duyệt shared simulations | ✅ | ✅ | ❌ | ❌ | ❌ |
| Remove simulations | ✅ | ✅ | ❌ | ❌ | ❌ |
| Feature simulations | ✅ | ✅ | ❌ | ❌ | ❌ |
| Handle reports | ✅ | ✅ | ❌ | ❌ | ❌ |
| **Simulation Creation** |
| Tạo simulation (AI) | ✅ | ✅ | ❌ | ✅ | ❌ |
| Edit own simulations | ✅ | ✅ | ❌ | ✅ | ❌ |
| Share to community | ✅ | ✅ | ❌ | ✅** | ❌ |
| Delete own simulations | ✅ | ✅ | ❌ | ✅ | ❌ |
| **Teaching & Learning** |
| Giao bài | ✅ | ✅ | ❌ | ✅ | ❌ |
| Chấm bài | ✅ | ✅ | ❌ | ✅ | ❌ |
| Xem báo cáo lớp | ✅ | ✅ | ✅ | ✅ | ❌ |
| Làm bài | ❌ | ❌ | ❌ | ❌ | ✅ |
| Xem điểm | ✅ | ✅ | ✅ (all) | ✅ (class) | ✅ (own) |
| **Library Access** |
| Personal library | ✅ | ✅ | ❌ | ✅ | ❌ |
| School library | ✅ | ✅ | ✅ (view) | ✅ | ✅ |
| Shared library (view) | ✅ | ✅ | ✅ | ✅ | ✅ |
| Shared library (upload) | ✅ | ✅ | ❌ | ✅** | ❌ |

*Teacher chỉ phân học sinh vào lớp mình dạy  
**Teacher share → PENDING_REVIEW → REVIEWER approve

### **Key Differences:**

- **SYSTEM_ADMIN**: Quản lý platform-level (all schools)
- **CONTENT_REVIEWER**: Quality control cho Shared Library
- **SCHOOL_ADMIN**: Quản lý operational của 1 trường (users, classes) - KHÔNG dạy
- **TEACHER**: Dạy học (create content, assign, grade) - Quản lý lớp của mình
- **STUDENT**: Học tập

### 4.3. Simulation Visibility Flow

```
[TEACHER creates simulation]
          │
          ▼
  ┌───────────────────┐
  │ PERSONAL LIBRARY  │ ← Only creator sees
  └───────────────────┘
          │
          │ Teacher assigns to class
          ▼
  ┌───────────────────┐
  │   USED IN CLASS   │ ← Students in class can access
  └───────────────────┘
          │
          │ Teacher clicks "Share to Community"
          ▼
  ┌───────────────────┐
  │ PENDING_REVIEW    │ ← Enters REVIEWER queue
  └───────────────────┘
          │
          │ REVIEWER reviews
          ▼
    ┌─────────┐
    │ Decision│
    └────┬────┘
         │
    ┌────┴────┐
    │         │
    ▼         ▼
 APPROVE   REJECT
    │         │
    │         └──► Back to PERSONAL (with feedback)
    │
    ▼
┌─────────────────────┐
│  SHARED LIBRARY     │ ← All schools can access
│  (Community)        │
└─────────────────────┘
```

---

## 5. DATABASE SCHEMA (UPDATED)

### 5.1. Users & Roles

```sql
-- =====================================================
-- USERS với PLATFORM vs SCHOOL roles
-- =====================================================

CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(255) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    full_name VARCHAR(255) NOT NULL,
    avatar_url TEXT,
    
    -- Role (CRITICAL!)
    role VARCHAR(20) NOT NULL,
    -- 'SYSTEM_ADMIN' | 'CONTENT_REVIEWER' | 'SCHOOL_ADMIN' | 'TEACHER' | 'STUDENT'
    
    -- School association
    school_id UUID REFERENCES schools(id) ON DELETE CASCADE,
    -- NULL for SYSTEM_ADMIN and CONTENT_REVIEWER (platform roles)
    -- NOT NULL for SCHOOL_ADMIN, TEACHER and STUDENT (school roles)
    
    -- Profile
    phone VARCHAR(20),
    date_of_birth DATE,
    gender VARCHAR(10),
    
    -- Student specific
    student_code VARCHAR(50),
    grade INTEGER, -- 10, 11, 12
    
    -- Teacher specific
    teacher_code VARCHAR(50),
    subject VARCHAR(100),
    
    -- Status
    is_active BOOLEAN DEFAULT true,
    is_verified BOOLEAN DEFAULT false,
    last_login TIMESTAMP,
    
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW(),
    
    -- Constraints
    CONSTRAINT check_platform_roles_no_school 
        CHECK (
            (role IN ('SYSTEM_ADMIN', 'CONTENT_REVIEWER') AND school_id IS NULL)
            OR
            (role IN ('SCHOOL_ADMIN', 'TEACHER', 'STUDENT') AND school_id IS NOT NULL)
        )
);

CREATE INDEX idx_users_role ON users(role);
CREATE INDEX idx_users_school ON users(school_id);
CREATE INDEX idx_users_email ON users(email);

-- =====================================================
-- LICENSES với AI QUOTA
-- =====================================================

CREATE TABLE license_plans (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(100) NOT NULL, -- Starter, Professional, Enterprise
    student_quota INTEGER NOT NULL,
    ai_tokens_monthly INTEGER, -- NULL = unlimited
    price_annual DECIMAL(15,2),
    features JSONB,
    is_active BOOLEAN DEFAULT true,
    created_at TIMESTAMP DEFAULT NOW()
);

CREATE TABLE licenses (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id UUID REFERENCES schools(id) ON DELETE CASCADE,
    plan_id UUID REFERENCES license_plans(id),
    
    -- License details
    license_key VARCHAR(255) UNIQUE NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    status VARCHAR(20) DEFAULT 'ACTIVE',
    
    -- Quota học sinh
    student_quota INTEGER NOT NULL,
    student_used INTEGER DEFAULT 0,
    
    -- Quota AI tokens (CRITICAL!)
    ai_tokens_monthly INTEGER, -- NULL = unlimited
    ai_tokens_used_current_month INTEGER DEFAULT 0,
    ai_tokens_reset_at TIMESTAMP, -- Reset mỗi tháng
    
    -- Storage quota
    storage_quota_gb INTEGER,
    storage_used_gb DECIMAL(10,2) DEFAULT 0,
    
    -- Billing
    payment_status VARCHAR(20),
    amount DECIMAL(15,2),
    
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

-- Track AI token usage
CREATE TABLE ai_token_usage_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    license_id UUID REFERENCES licenses(id),
    user_id UUID REFERENCES users(id), -- Giáo viên nào dùng
    operation VARCHAR(50), -- CREATE_SIMULATION, CUSTOMIZE_SIMULATION
    tokens_used INTEGER NOT NULL,
    prompt_tokens INTEGER,
    completion_tokens INTEGER,
    metadata JSONB, -- {simulation_id, complexity, etc.}
    created_at TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_ai_usage_license ON ai_token_usage_logs(license_id, created_at);

-- =====================================================
-- SIMULATIONS LIBRARY
-- =====================================================

CREATE TABLE simulations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    title VARCHAR(255) NOT NULL,
    description TEXT,
    
    -- Content
    specification JSONB NOT NULL, -- AI-generated spec
    scene_graph JSONB, -- Compiled scene
    physics_config JSONB, -- Physics parameters
    thumbnail_url TEXT,
    
    -- Metadata
    subject VARCHAR(50) DEFAULT 'Physics',
    topic VARCHAR(100), -- Cơ học, Quang học, etc.
    grade INTEGER, -- 10, 11, 12
    difficulty VARCHAR(20), -- EASY, MEDIUM, HARD
    estimated_time INTEGER, -- phút
    
    -- Ownership & Visibility
    created_by UUID REFERENCES users(id),
    school_id UUID REFERENCES schools(id),
    visibility VARCHAR(20) DEFAULT 'PERSONAL', -- PERSONAL, SHARED
    
    -- AI creation tracking
    ai_tokens_used INTEGER, -- Tokens tốn khi tạo
    ai_generation_metadata JSONB,
    
    -- Usage stats
    times_used INTEGER DEFAULT 0,
    times_cloned INTEGER DEFAULT 0,
    avg_rating DECIMAL(3,2),
    
    -- Status
    status VARCHAR(20) DEFAULT 'DRAFT', -- DRAFT, PUBLISHED, ARCHIVED
    validation_status VARCHAR(20), -- PENDING, VALIDATED, FAILED
    
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_simulations_visibility ON simulations(visibility, status);
CREATE INDEX idx_simulations_grade ON simulations(grade);
CREATE INDEX idx_simulations_created_by ON simulations(created_by);

-- =====================================================
-- ASSIGNMENTS với AUTO-GRADING
-- =====================================================

CREATE TABLE assignments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    class_id UUID REFERENCES classes(id) ON DELETE CASCADE,
    teacher_id UUID REFERENCES users(id),
    simulation_id UUID REFERENCES simulations(id),
    
    title VARCHAR(255) NOT NULL,
    description TEXT,
    instructions TEXT,
    
    -- Timing
    assigned_at TIMESTAMP DEFAULT NOW(),
    due_date TIMESTAMP,
    
    -- Grading config
    max_score DECIMAL(5,2) DEFAULT 10.0,
    auto_grading_enabled BOOLEAN DEFAULT true,
    grading_criteria JSONB, -- AI grading rules
    -- Example:
    -- {
    --   "questions": [
    --     {
    --       "id": "q1",
    --       "text": "Tính thời gian rơi",
    --       "formula": "h = 0.5*g*t^2",
    --       "expected_result": 2.0,
    --       "unit": "s",
    --       "tolerance": 0.1,
    --       "formula_points": 2,
    --       "result_points": 2
    --     }
    --   ]
    -- }
    
    -- Settings
    allow_late_submission BOOLEAN DEFAULT false,
    max_attempts INTEGER DEFAULT 999, -- Unlimited by default
    
    is_active BOOLEAN DEFAULT true,
    created_at TIMESTAMP DEFAULT NOW()
);

-- =====================================================
-- SUBMISSIONS với ACTION LOGS
-- =====================================================

CREATE TABLE submissions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    assignment_id UUID REFERENCES assignments(id) ON DELETE CASCADE,
    student_id UUID REFERENCES users(id) ON DELETE CASCADE,
    
    -- Submission data
    answers JSONB, -- Student's answers
    -- Example:
    -- {
    --   "q1": {
    --     "formula": "h = 1/2 * g * t^2",
    --     "result": 2.0,
    --     "unit": "s"
    --   }
    -- }
    
    action_logs JSONB, -- Student's actions during simulation
    -- See example in section 3.2 [10]
    
    time_spent INTEGER, -- seconds
    attempt_number INTEGER DEFAULT 1,
    
    -- AI Auto-grading
    ai_score DECIMAL(5,2), -- AI suggested score
    ai_grading_details JSONB,
    -- Example:
    -- {
    --   "q1": {
    --     "formula_correct": true,
    --     "formula_points": 2,
    --     "result_correct": true,
    --     "result_points": 2,
    --     "subtotal": 4
    --   },
    --   "total": 10,
    --   "confidence": 0.95
    -- }
    
    -- Final grading (after teacher review)
    final_score DECIMAL(5,2),
    teacher_feedback TEXT,
    graded_by UUID REFERENCES users(id),
    
    -- Status
    status VARCHAR(20) DEFAULT 'SUBMITTED', 
    -- SUBMITTED, AUTO_GRADED, PENDING_REVIEW, GRADED, REVISION_REQUESTED
    
    submitted_at TIMESTAMP DEFAULT NOW(),
    graded_at TIMESTAMP,
    
    UNIQUE(assignment_id, student_id, attempt_number)
);

CREATE INDEX idx_submissions_status ON submissions(assignment_id, status);
```

---

## 5. API ENDPOINTS (UPDATED)

### 5.1. AI Simulation Creation

```
POST   /api/simulations/create-with-ai
       • Tạo simulation bằng AI
       • Request:
         {
           "description": "Mô tả đề bài...",
           "image_url": "https://...", // optional
           "grade": 10,
           "topic": "Cơ học"
         }
       • Response:
         {
           "conversation_id": "uuid",
           "status": "analyzing",
           "ambiguities": [
             {
               "code": "air_resistance",
               "question": "Có xét sức cản không khí không?",
               "options": ["Có", "Không"]
             }
           ]
         }

POST   /api/simulations/create-with-ai/answer
       • Trả lời câu hỏi của AI
       • Request:
         {
           "conversation_id": "uuid",
           "answers": {
             "air_resistance": "Không"
           }
         }

POST   /api/simulations/create-with-ai/confirm
       • Xác nhận specification & tạo simulation
       • Request:
         {
           "conversation_id": "uuid",
           "specification": {...},
           "visibility": "PERSONAL" | "SHARED"
         }
       • Response:
         {
           "simulation_id": "uuid",
           "ai_tokens_used": 1250,
           "remaining_tokens": 98750
         }

GET    /api/simulations/library/personal
       • Lấy simulations của giáo viên

GET    /api/simulations/library/shared
       • Lấy simulations shared (public library)

POST   /api/simulations/{id}/clone
       • Clone simulation từ shared library
       • Không tốn AI tokens
```

### 5.2. Auto-Grading

```
POST   /api/submissions
       • Nộp bài
       • Request:
         {
           "assignment_id": "uuid",
           "answers": {...},
           "action_logs": [...]
         }
       • Response:
         {
           "submission_id": "uuid",
           "ai_score": 9.5,
           "ai_grading_details": {...},
           "status": "AUTO_GRADED"
         }

PUT    /api/submissions/{id}/review
       • Giáo viên review & confirm điểm
       • Request:
         {
           "final_score": 9.5,
           "feedback": "Làm tốt!",
           "status": "GRADED" | "REVISION_REQUESTED"
         }

GET    /api/submissions/{id}/replay
       • Lấy action logs để replay
       • Response:
         {
           "action_logs": [...],
           "timeline": [
             {"time": 0, "action": "start"},
             {"time": 5, "action": "run_simulation", "params": {...}},
             {"time": 15, "action": "take_measurement", ...}
           ]
         }
```

### 5.3. Quota Management

```
GET    /api/licenses/{id}/quota
       • Lấy thông tin quota hiện tại
       • Response:
         {
           "student_quota": 1000,
           "student_used": 823,
           "ai_tokens_monthly": 300000,
           "ai_tokens_used": 145230,
           "ai_tokens_remaining": 154770,
           "reset_at": "2026-10-01T00:00:00Z",
           "usage_alerts": [
             {
               "type": "warning",
               "message": "Đã sử dụng 48% quota AI tháng này"
             }
           ]
         }

GET    /api/licenses/{id}/ai-usage-history
       • Lịch sử sử dụng AI tokens
       • Query: ?from=2026-09-01&to=2026-09-30
       • Response:
         {
           "total_tokens": 145230,
           "by_operation": {
             "CREATE_SIMULATION": 120000,
             "CUSTOMIZE_SIMULATION": 25230
           },
           "by_teacher": [
             {"teacher_id": "uuid", "name": "Nguyễn Văn A", "tokens": 45000},
             {"teacher_id": "uuid", "name": "Trần Thị B", "tokens": 38000}
           ],
           "daily_usage": [
             {"date": "2026-09-01", "tokens": 4500},
             {"date": "2026-09-02", "tokens": 5200}
           ]
         }

POST   /api/licenses/{id}/buy-ai-tokens
       • Mua thêm AI tokens
       • Request:
         {
           "package": "50k" | "100k" | "custom",
           "amount": 50000
         }
```

---

## 6. MVP SCOPE & ROADMAP

### 6.1. MVP Features (3 Tháng)

```
PHASE 1: FOUNDATION (Tháng 1)
✅ Core Infrastructure
  - Backend API: Spring Boot + PostgreSQL
  - Frontend: React + Canvas renderer (sử dụng code hiện tại)
  - Authentication: JWT + 4 roles
  - Database setup

✅ School Registration Flow
  - School đăng ký
  - System Admin duyệt
  - School Manager account
  - Basic license activation (manual payment)

✅ User Management
  - CRUD users (School Manager)
  - Import CSV (giáo viên, học sinh)
  - Class management

PHASE 2: AI SIMULATION (Tháng 2)
✅ AI Simulation Creation
  - OpenAI API integration
  - Chat interface (sử dụng CreateSimulationModal hiện tại)
  - Specification generation
  - 3 mô phỏng MVP:
    1. Chuyển động thẳng đều
    2. Chuyển động rơi tự do
    3. Định luật Newton II (F=ma)

✅ Personal & Shared Library
  - Personal library (CRUD)
  - Share simulation
  - Clone simulation

✅ Quota Tracking
  - AI tokens usage
  - Student quota
  - Alerts khi gần hết

PHASE 3: ASSIGNMENT & GRADING (Tháng 3)
✅ Assignment Creation
  - Chọn simulation
  - Setup grading criteria
  - Assign to class

✅ Student Interface
  - View assignments
  - Run simulation (sử dụng CanvasPhysicsScene)
  - Submit answers
  - Action logging

✅ Auto-Grading
  - AI formula checking
  - Result validation
  - Teacher review UI
  - Replay action logs

✅ Basic Analytics
  - Student progress
  - Class overview
  - Teacher dashboard
```

### 6.2. 3 Mô Phỏng MVP

```
1. CHUYỂN ĐỘNG THẲNG ĐỀU (Lớp 10)
   Mô tả: Vật chuyển động với vận tốc không đổi
   Input params:
     - Vận tốc v (m/s)
     - Thời gian t (s)
   Output:
     - Quãng đường s = v × t
   Độ phức tạp: ⭐ (Đơn giản)
   AI tokens ước tính: ~500 tokens

2. CHUYỂN ĐỘNG RƠI TỰ DO (Lớp 10)
   Mô tả: Vật rơi từ độ cao h0, không vận tốc ban đầu
   Input params:
     - Độ cao ban đầu h0 (m)
     - Gia tốc trọng trường g (m/s²)
   Output:
     - Thời gian rơi t = √(2h0/g)
     - Vận tốc chạm đất v = √(2gh0)
   Độ phức tạp: ⭐⭐ (Trung bình)
   AI tokens ước tính: ~800 tokens

3. ĐỊNH LUẬT NEWTON II (Lớp 10)
   Mô tả: Vật chịu tác dụng lực F, khối lượng m
   Input params:
     - Lực tác dụng F (N)
     - Khối lượng m (kg)
   Output:
     - Gia tốc a = F/m (m/s²)
   Visualization: Vật di chuyển với gia tốc a
   Độ phức tạp: ⭐⭐ (Trung bình)
   AI tokens ước tính: ~700 tokens
```

### 6.3. Post-MVP (Tháng 4-6)

```
PHASE 4: EXPANSION
- Thêm 10+ mô phỏng cho lớp 10-12
- WebSocket real-time updates
- Advanced analytics
- Export data (Excel/PDF)

PHASE 5: POLISH & SCALE
- Payment integration (VNPay)
- Mobile responsive
- Performance optimization
- Pilot với 5-10 trường
```

---

## 7. TECH STACK CONFIRMATION

### 7.1. Đã Có (Sử Dụng Lại)

```
✅ Frontend:
  - React 18 + TypeScript
  - Vite
  - Canvas rendering (CanvasPhysicsScene.tsx)
  - Simulation runtime (SimulationRuntime.ts)
  - Physics scene compiler (SceneCompiler.ts)

✅ Backend:
  - Spring Boot 3.3.4
  - Java 21
  - PostgreSQL (Supabase)
  - JWT authentication
  - Swagger/OpenAPI

✅ Infrastructure:
  - Docker
  - Git/GitHub
```

### 7.2. Cần Bổ Sung

```
🆕 AI Integration:
  - OpenAI API client
  - Token counting & tracking
  - Prompt engineering for simulation generation

🆕 Auto-Grading Engine:
  - Formula parser (parse "h = 1/2 * g * t^2")
  - Expression evaluator
  - Tolerance checking

🆕 Physics Engine:
  - Matter.js hoặc Cannon.js
  - Hoặc tiếp tục dùng custom engine hiện tại

🆕 Payment:
  - VNPay SDK (Phase 4)
  - Momo API (Phase 4)
```

---

## 8. RISKS & MITIGATION

### 8.1. Technical Risks

| Risk | Impact | Probability | Mitigation |
|------|--------|-------------|------------|
| **OpenAI API costs cao hơn dự tính** | High | Medium | - Set hard limits per school<br>- Cache simulations tương tự<br>- Optimize prompts |
| **Physics rendering không chính xác** | High | Low | - Validate với giáo viên vật lý<br>- Unit tests cho physics engine |
| **Auto-grading sai** | Medium | Medium | - Giáo viên always review<br>- Confidence threshold<br>- Feedback loop |
| **Scalability issues** | Medium | Low | - Load testing<br>- Redis caching<br>- CDN for assets |

### 8.2. Business Risks

| Risk | Impact | Probability | Mitigation |
|------|--------|-------------|------------|
| **Trường không mua vì giá cao** | High | Medium | - Pilot program miễn phí 3 tháng<br>- Flexible pricing<br>- ROI calculator |
| **Giáo viên không dùng (too complex)** | High | Low | - Onboarding training<br>- Video tutorials<br>- 24/7 support |
| **Competitors** | Medium | Medium | - Focus on Vietnam market<br>- Tiếng Việt + chương trình VN<br>- AI advantage |

---

## 9. SUCCESS METRICS (KPIs)

### 9.1. Product Metrics

```
MONTH 3 (MVP Launch):
  - 3 simulations live ✅
  - 3-5 trường pilot ✅
  - 10+ giáo viên active ✅
  - 100+ học sinh test ✅

MONTH 6:
  - 15+ simulations ✅
  - 20 trường paying customers ✅
  - 300+ giáo viên active ✅
  - 10,000+ students ✅
  - 50+ simulations in shared library ✅

MONTH 12:
  - 50+ simulations ✅
  - 100 trường ✅
  - 1,500+ giáo viên ✅
  - 50,000+ students ✅
  - Revenue: 5.8 tỷ VNĐ ✅
```

### 9.2. Engagement Metrics

```
- Simulation creation rate: >5 per teacher per month
- Shared library contribution: >20% teachers share
- Assignment completion rate: >80%
- Auto-grading accuracy: >90%
- Teacher review acceptance: >95% (accept AI score)
- Student satisfaction (NPS): >70
```

---

## 10. APPENDIX

### 10.1. Glossary

- **Simulation**: Mô phỏng vật lý tương tác
- **Specification**: Spec JSON mô tả simulation (objects, quantities, relations)
- **Scene Graph**: Compiled representation của simulation
- **Action Log**: Log các thao tác của học sinh khi làm bài
- **Quota AI**: Số tokens AI có thể dùng để tạo simulation
- **Personal Library**: Thư viện mô phỏng riêng của giáo viên
- **Shared Library**: Thư viện mô phỏng công cộng (mọi trường dùng được)

### 10.2. References

- Dự án hiện tại: `d:\FPT_FALL_2026\SEP490\physLive_preview`
- Code tham khảo:
  - `react-client/src/components/workspace/CreateSimulationModal.tsx`
  - `react-client/src/components/simulation-canvas/CanvasPhysicsScene.tsx`
  - `react-client/src/pages/library/Library.tsx`

---

**END OF DOCUMENT**

*Tài liệu này được cập nhật dựa trên requirements thực tế và code base hiện có.*
*Phiên bản 2.0 - 17/09/2026*
