import { Avatar, Badge, Button, Card, Heading, Spinner, Text } from "@radix-ui/themes";
import { motion, useReducedMotion } from "motion/react";
import type { StudentClassSummary } from "../../../api/schoolApi";
import LearningIcon from "../../common/LearningIcon";

type Props = {
  studentName: string;
  schoolName?: string | null;
  classes: StudentClassSummary[];
  loading: boolean;
  pendingAssignments: number;
  completedAssignments: number;
  sharedResources: number;
  onExplore: () => void;
};

export function StudentClassOverview({ studentName, schoolName, classes, loading, pendingAssignments, completedAssignments, sharedResources, onExplore }: Readonly<Props>) {
  const reducedMotion = useReducedMotion();
  const stats = [
    { label: "Bài cần hoàn thành", value: pendingAssignments, color: "amber", icon: "file" },
    { label: "Bài đã nộp", value: completedAssignments, color: "cyan", icon: "check" },
    { label: "Lớp học", value: loading ? "—" : classes.length, color: "indigo", icon: "users" },
  ] as const;
  return <motion.div initial={reducedMotion ? false : { opacity: 0, y: 8 }} animate={{ opacity: 1, y: 0 }} transition={{ duration: .18 }}>
    <header className="student-page-heading">
      <div><Heading as="h1" size="7">Học tập</Heading><Text as="p" size="3" color="gray">{studentName}{schoolName && <><span aria-hidden="true"> · </span>{schoolName}</>}</Text></div>
      <Button type="button" size="2" onClick={onExplore}><LearningIcon name="book" />Cộng đồng<Badge color="gray" variant="soft">{sharedResources}</Badge></Button>
    </header>
    <section className="student-summary" aria-label="Tổng quan học tập">{stats.map(stat => <Card asChild key={stat.label} className={`student-summary-card student-stat-${stat.color}`} size="3"><motion.article whileHover={reducedMotion ? undefined : { y: -3 }} transition={{ duration: .18 }}><span className="student-stat-tile"><LearningIcon name={stat.icon} /></span><div><Text as="div" size="2" color="gray">{stat.label}</Text><Heading as="h2" size="8">{stat.value}</Heading></div></motion.article></Card>)}</section>
    <section className="student-classes" aria-labelledby="student-classes-title">
      <div className="student-section-title"><Heading as="h2" size="4" id="student-classes-title">Lớp của tôi</Heading><Badge color="indigo" variant="soft">{loading ? "Đang tải…" : `${classes.length} lớp`}</Badge></div>
      {loading ? <Card className="student-quiet-state"><Spinner size="2" /><Text color="gray">Đang tải thông tin lớp…</Text></Card> : classes.length === 0 ? <Card className="student-quiet-state"><Text color="gray">Chưa được xếp vào lớp học.</Text></Card> :
        <div className="student-class-list">{classes.map(item => <Card asChild className="student-class-card" key={item.id} size="3"><motion.article whileHover={reducedMotion ? undefined : { y: -2 }} transition={{ duration: .18 }}>
          <div className="student-class-code"><Heading as="h3" size="5">{item.name}</Heading></div>
          <div className="student-class-heading"><Badge color="indigo" variant="soft" size="2">Lớp {item.gradeLevel}</Badge><Text as="p" size="3" weight="medium">{item.subject || "Vật lý"}</Text><Text size="1" color="gray">{item.schoolYear}</Text></div>
          <div className="student-class-teacher"><Avatar size="2" color="cyan" radius="full" fallback={item.teachers[0]?.fullName.trim().charAt(0) || "GV"} /><Text size="2">{item.teachers.length ? item.teachers.map(teacher => teacher.fullName).join(", ") : "Chưa phân công giáo viên"}</Text><Text size="1" color="gray" className="student-class-size">{item.classmateCount} bạn cùng lớp</Text></div>
        </motion.article></Card>)}</div>}
    </section>
  </motion.div>;
}
