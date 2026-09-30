import { useState } from "react";
import { Badge, Button, Callout, Card, Heading, SegmentedControl, Select, Spinner, Table, Text, TextField, VisuallyHidden } from "@radix-ui/themes";
import { motion, useReducedMotion } from "motion/react";
import type { Assignment, AssignmentActivityType } from "../../../types/physlive";
import { useAssignmentClock } from "../../../utils/useAssignmentClock";
import LearningIcon from "../../common/LearningIcon";

type AssignmentListProps = {
  assignments: Assignment[];
  loading: boolean;
  error: string;
  onRefresh: () => void;
  onSelect: (item: Assignment) => void;
};
type Filter = "todo" | "submitted" | "all";
const needsWork = (item: Assignment) => !item.submissionCompleted || item.retryAllowed;
const activityLabels: Record<AssignmentActivityType, string> = {
  PREDICT_OBSERVE_EXPLAIN: "Dự đoán · Quan sát · Giải thích",
  MEASUREMENT: "Đo lường",
  PARAMETER_INVESTIGATION: "Khảo sát thông số",
  FREE_EXPLORATION: "Khám phá mô phỏng",
};

export function AssignmentList({ assignments, loading, error, onRefresh, onSelect }: Readonly<AssignmentListProps>) {
  const [filter, setFilter] = useState<Filter>("todo");
  const [search, setSearch] = useState("");
  const [classFilter, setClassFilter] = useState("");
  const reducedMotion = useReducedMotion();
  const now = useAssignmentClock();
  const pending = assignments.filter(needsWork).length;
  const classes = Array.from(new Map(assignments.filter(item => item.classId).map(item => [item.classId!, item.className || "Lớp học"])).entries());
  const keyword = search.trim().toLocaleLowerCase("vi");
  const visible = assignments.filter(item =>
    (filter === "all" || (filter === "todo" ? needsWork(item) : !needsWork(item))) &&
    (!classFilter || item.classId === classFilter) &&
    [item.title, item.className, item.libraryItemTitle].filter(Boolean).join(" ").toLocaleLowerCase("vi").includes(keyword),
  ).sort((a, b) => Number(Boolean(b.retryAllowed)) - Number(Boolean(a.retryAllowed)) ||
    (a.dueAt ? new Date(a.dueAt).getTime() : Infinity) - (b.dueAt ? new Date(b.dueAt).getTime() : Infinity) ||
    new Date(b.assignedAt).getTime() - new Date(a.assignedAt).getTime());
  const counts: Record<Filter, number> = { todo: pending, submitted: assignments.length - pending, all: assignments.length };

  return <Card asChild className="student-assignment-list" size="3"><motion.section aria-label="Bài tập được giao" initial={reducedMotion ? false : { opacity: 0, y: 8 }} animate={{ opacity: 1, y: 0 }} transition={{ duration: .18 }}>
    <div className="student-assignment-toolbar">
      <SegmentedControl.Root value={filter} onValueChange={value => setFilter(value as Filter)} size="2" aria-label="Trạng thái bài tập">{([["todo", "Cần làm"], ["submitted", "Đã nộp"], ["all", "Tất cả"]] as const).map(([value, label]) => <SegmentedControl.Item key={value} value={value}>{label}<span className="student-filter-count">{counts[value]}</span></SegmentedControl.Item>)}</SegmentedControl.Root>
      <div className="student-assignment-search">
        <TextField.Root type="search" size="2" aria-label="Tìm bài tập" placeholder="Tìm bài tập, lớp học…" value={search} onChange={event => setSearch(event.target.value)}><TextField.Slot><LearningIcon name="search" /></TextField.Slot></TextField.Root>
        {classes.length > 1 && <Select.Root value={classFilter || "all"} onValueChange={value => setClassFilter(value === "all" ? "" : value)}><Select.Trigger aria-label="Lọc theo lớp học" /><Select.Content><Select.Item value="all">Tất cả lớp</Select.Item>{classes.map(([id, label]) => <Select.Item key={id} value={id}>{label}</Select.Item>)}</Select.Content></Select.Root>}
        <Button type="button" variant="surface" disabled={loading} onClick={onRefresh}><LearningIcon name="refresh" />{loading ? "Đang tải…" : "Làm mới"}</Button>
      </div>
    </div>
    {error && <Callout.Root color="red" size="1" className="student-inline-alert"><Callout.Text>{error}</Callout.Text><Button type="button" variant="ghost" color="red" onClick={onRefresh}>Thử lại</Button></Callout.Root>}
    {loading ? <div className="student-list-state" role="status"><Spinner size="3" /><Text color="gray">Đang tải bài tập…</Text></div> : !error && visible.length === 0 ? <div className="student-list-state"><span className="student-empty-icon"><LearningIcon name="book" /></span><Heading as="h3" size="4">{assignments.length === 0 ? "Chưa có bài tập được giao" : "Không tìm thấy bài tập"}</Heading><Text as="p" color="gray">{assignments.length === 0 ? "Bài tập của giáo viên sẽ xuất hiện tại đây." : "Thử đổi từ khóa hoặc bộ lọc."}</Text>{assignments.length > 0 && <Button type="button" variant="soft" onClick={() => { setFilter("all"); setSearch(""); setClassFilter(""); }}>Xem tất cả</Button>}</div> : !error && <Table.Root className="student-assignment-table" variant="ghost" size="2"><Table.Header><Table.Row><Table.ColumnHeaderCell>Bài tập</Table.ColumnHeaderCell><Table.ColumnHeaderCell>Hạn nộp</Table.ColumnHeaderCell><Table.ColumnHeaderCell>Trạng thái</Table.ColumnHeaderCell><Table.ColumnHeaderCell><VisuallyHidden>Thao tác</VisuallyHidden></Table.ColumnHeaderCell></Table.Row></Table.Header><Table.Body>{visible.map(item => {
      const overdue = Boolean(item.dueAt && new Date(item.dueAt).getTime() < now);
      const status = item.retryAllowed ? "Cần làm lại" : item.gradingStatus === "TEACHER_CONFIRMED" ? "Đã chấm" : item.submissionCompleted ? "Chờ chấm" : item.predictionSubmitted ? "Đang làm" : overdue ? "Quá hạn" : "Chưa làm";
      const color = item.retryAllowed || (!item.submissionCompleted && overdue) ? "amber" : item.submissionCompleted ? "cyan" : item.predictionSubmitted ? "indigo" : "gray";
      const activityType = typeof item.questions === "object" && item.questions ? item.questions.activityType || "PREDICT_OBSERVE_EXPLAIN" : "PREDICT_OBSERVE_EXPLAIN";
      const action = item.retryAllowed ? "Làm lại" : item.submissionCompleted ? "Xem bài" : item.predictionSubmitted ? "Tiếp tục" : "Làm bài";
      return <Table.Row key={item.id}>
        <Table.Cell className="student-assignment-title"><Heading as="h3" size="3">{item.title}</Heading><Text as="p" color="gray" size="2">{item.className && <>{item.className}<span aria-hidden="true"> · </span></>}{activityLabels[activityType]}</Text>{item.feedback && <details className="student-assignment-feedback"><summary>Nhận xét của giáo viên</summary><p>{item.feedback}</p></details>}</Table.Cell>
        <Table.Cell className="student-assignment-deadline">{item.dueAt ? <><Text asChild size="2"><time dateTime={item.dueAt}>{new Date(item.dueAt).toLocaleDateString("vi-VN")}</time></Text><Text as="div" color="gray" size="1">{new Date(item.dueAt).toLocaleTimeString("vi-VN", { hour: "2-digit", minute: "2-digit" })}</Text></> : <Text color="gray" size="2">Không giới hạn</Text>}</Table.Cell>
        <Table.Cell><Badge color={color} size="2">{status}</Badge>{item.score != null && !item.retryAllowed && <Text as="div" className="student-assignment-score" size="1" color="gray">{item.score}/{item.maxScore ?? 10}{item.gradingStatus === "AI_GRADED" ? " · Tạm tính" : ""}</Text>}</Table.Cell>
        <Table.Cell className="student-assignment-action"><Button type="button" variant={item.submissionCompleted && !item.retryAllowed ? "soft" : "solid"} onClick={() => onSelect(item)} aria-label={`${action}: ${item.title}`}>{action}<LearningIcon name="arrow" /></Button></Table.Cell>
      </Table.Row>;
    })}</Table.Body></Table.Root>}
  </motion.section></Card>;
}
