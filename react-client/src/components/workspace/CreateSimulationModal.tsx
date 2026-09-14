import { useState } from "react";
import "../../styles/workspace-modal.css";

interface CreateTopicModalProps {
  isOpen: boolean;
  onClose: () => void;
  onCreate: (topic: string, grade: number) => void;
  initialTopic?: string;
  initialGrade?: number;
  isEditing?: boolean;
}

export default function CreateSimulationModal({
  isOpen,
  onClose,
  onCreate,
  initialTopic = "",
  initialGrade = 10,
  isEditing = false,
}: CreateTopicModalProps) {
  const [topic, setTopic] = useState(initialTopic);
  const [grade, setGrade] = useState(initialGrade);

  if (!isOpen) return null;

  const handleSubmit = (event: React.FormEvent) => {
    event.preventDefault();
    if (!topic.trim()) return;
    onCreate(topic.trim(), grade);
    onClose();
  };

  return (
    <>
      <div className="modal-overlay" onClick={onClose} />
      <div className="modal-container modal-container-compact">
        <div className="modal-header">
          <div className="modal-title-group">
            <h2 className="modal-title">
              {isEditing ? "Chỉnh sửa topic" : "Tạo topic mới"}
            </h2>
            <p className="modal-subtitle">
              Giáo viên tự đặt tiêu đề bài học và có thể đổi tên bất kỳ lúc nào.
            </p>
          </div>
          <button className="modal-close-btn" onClick={onClose} aria-label="Đóng">
            ✕
          </button>
        </div>

        <form onSubmit={handleSubmit} className="modal-body">
          <div className="modal-input-section">
            <label htmlFor="topic-name" className="modal-label">Tên topic / lesson</label>
            <input
              id="topic-name"
              className="modal-select"
              type="text"
              value={topic}
              onChange={(event) => setTopic(event.target.value)}
              placeholder="Ví dụ: Chuyển động thẳng biến đổi đều"
              autoFocus
            />
          </div>

          <div className="modal-input-section">
            <label htmlFor="topic-grade" className="modal-label">Lớp</label>
            <select
              id="topic-grade"
              className="modal-select"
              value={grade}
              onChange={(event) => setGrade(Number(event.target.value))}
            >
              {[10, 11, 12].map((item) => (
                <option key={item} value={item}>Lớp {item}</option>
              ))}
            </select>
          </div>

          <div className="modal-footer">
            <button type="button" className="modal-btn modal-btn-cancel" onClick={onClose}>
              Hủy
            </button>
            <button
              type="submit"
              className="modal-btn modal-btn-primary"
              disabled={!topic.trim()}
            >
              <span>{isEditing ? "✓" : "＋"}</span>
              {isEditing ? "Lưu thay đổi" : "Tạo topic"}
            </button>
          </div>
        </form>
      </div>
    </>
  );
}
