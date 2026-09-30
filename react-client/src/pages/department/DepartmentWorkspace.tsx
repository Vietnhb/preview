import { useState } from "react";
import { Badge, Button, Card, Heading, Select, Table, Tabs, TextField, Theme } from "@radix-ui/themes";
import { usePhysliveStore } from "../../store/usePhysliveStore";
import SchoolClasses from "../school/SchoolClasses";
import { SharedLibraryTab } from "../../components/roles/reviewer/SharedLibraryTab";
import { LoadState } from "../../components/operations/OperationsKit";
import { useResource } from "../../components/operations/operationsData";
import academic from "../../app/AcademicChrome.module.css";
import styles from "./DepartmentWorkspace.module.css";
import "../reviewer/reviewer.css";

type Assignment = { id: string; title: string; className: string | null; schoolYear: string | null; teacherId: number; teacherName: string; studentCount: number; status: string; assignedAt: string; dueAt: string | null };
type AssignmentPage = { content: Assignment[]; totalElements: number; totalPages: number; number: number };

function SchoolAssignments({ schoolId }: { schoolId: string }) {
  const [page, setPage] = useState(0);
  const [query, setQuery] = useState("");
  const [teacher, setTeacher] = useState("ALL");
  const resource = useResource<AssignmentPage>(`/schools/${schoolId}/assignments?page=${page}&size=30`);
  const items = resource.data?.content ?? [];
  const teachers = [...new Map(items.map(item => [item.teacherId, item.teacherName])).entries()];
  const rows = items.filter(item => (teacher === "ALL" || String(item.teacherId) === teacher) && `${item.title} ${item.teacherName} ${item.className ?? ""}`.toLocaleLowerCase("vi").includes(query.toLocaleLowerCase("vi")));
  return <Card size="3">
    <div className={styles.heading}><Heading as="h2" size="5">Phân công bài tập</Heading><Button variant="soft" color="gray" onClick={resource.refresh}>Làm mới</Button></div>
    <LoadState {...resource} />
    <div className={styles.filters}><TextField.Root aria-label="Tìm bài tập trong trang" placeholder="Tìm bài tập, lớp hoặc giáo viên trong trang" value={query} onChange={event => setQuery(event.target.value)} /><Select.Root value={teacher} onValueChange={setTeacher}><Select.Trigger aria-label="Giáo viên trong trang" /><Select.Content><Select.Item value="ALL">Tất cả giáo viên trong trang</Select.Item>{teachers.map(([id, name]) => <Select.Item key={id} value={String(id)}>{name}</Select.Item>)}</Select.Content></Select.Root></div>
    <div className={styles.table}><Table.Root variant="surface"><Table.Header><Table.Row><Table.ColumnHeaderCell>Bài tập</Table.ColumnHeaderCell><Table.ColumnHeaderCell>Giáo viên</Table.ColumnHeaderCell><Table.ColumnHeaderCell>Lớp</Table.ColumnHeaderCell><Table.ColumnHeaderCell>Học sinh</Table.ColumnHeaderCell><Table.ColumnHeaderCell>Hạn nộp</Table.ColumnHeaderCell></Table.Row></Table.Header><Table.Body>{rows.map(item => <Table.Row key={item.id}><Table.RowHeaderCell>{item.title}<div><Badge color={item.status === "ACTIVE" ? "indigo" : "gray"} mt="2">{item.status === "ACTIVE" ? "Đang giao" : "Đã đóng"}</Badge></div></Table.RowHeaderCell><Table.Cell>{item.teacherName}</Table.Cell><Table.Cell>{item.className || "Giao trực tiếp"}<small className={styles.muted}>{item.schoolYear}</small></Table.Cell><Table.Cell>{item.studentCount}</Table.Cell><Table.Cell>{item.dueAt ? new Date(item.dueAt).toLocaleString("vi-VN") : "Không giới hạn"}</Table.Cell></Table.Row>)}{!resource.loading && rows.length === 0 && <Table.Row><Table.Cell colSpan={5}>Chưa có bài tập phù hợp.</Table.Cell></Table.Row>}</Table.Body></Table.Root></div>
    <div className={styles.pagination}><span>{resource.data?.totalElements ?? 0} bài tập · Trang {page + 1}/{Math.max(1, resource.data?.totalPages ?? 0)}</span><Button variant="soft" color="gray" disabled={page === 0 || resource.loading} onClick={() => { setTeacher("ALL"); setPage(value => value - 1); }}>Trước</Button><Button variant="soft" disabled={resource.loading || page + 1 >= (resource.data?.totalPages ?? 0)} onClick={() => { setTeacher("ALL"); setPage(value => value + 1); }}>Sau</Button></div>
  </Card>;
}

export default function DepartmentWorkspace() {
  const user = usePhysliveStore(state => state.user);
  if (!user?.schoolId || user.staffType !== "DEPARTMENT_HEAD") return null;
  return <Theme accentColor="indigo" grayColor="slate" radius="large" className={`${academic.chrome} ${styles.page}`}>
    <header className={styles.heading}><div><Badge color="indigo" mb="2">Trưởng bộ môn</Badge><Heading as="h1" size="7">Quản lý chuyên môn</Heading></div><span>{user.schoolName}</span></header>
    <Tabs.Root defaultValue="classes"><Tabs.List><Tabs.Trigger value="classes">Lớp & phân công</Tabs.Trigger><Tabs.Trigger value="assignments">Bài tập trong trường</Tabs.Trigger><Tabs.Trigger value="library">Duyệt nội dung nội bộ</Tabs.Trigger></Tabs.List><Tabs.Content value="classes" className={styles.content}><SchoolClasses /></Tabs.Content><Tabs.Content value="assignments" className={styles.content}><SchoolAssignments schoolId={user.schoolId} /></Tabs.Content><Tabs.Content value="library" className={`${styles.content} reviewer-academic`}><SharedLibraryTab schoolId={user.schoolId} /></Tabs.Content></Tabs.Root>
  </Theme>;
}
