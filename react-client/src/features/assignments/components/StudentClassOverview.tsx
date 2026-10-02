import { Avatar, Badge, Card, Heading, Spinner, Text } from "@radix-ui/themes";
import { motion, useReducedMotion } from "motion/react";
import type { StudentClassSummary } from "../../school/api/schoolApi";
import LearningIcon from "../../../shared/ui/LearningIcon";
import DotField from "../../../shared/effects/DotField";

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
  const givenName = studentName.trim().split(/\s+/).at(-1) || studentName;
  const message = pendingAssignments > 0
    ? `Bạn có ${pendingAssignments} bài tập cần hoàn thành.`
    : completedAssignments > 0 ? "Bạn đã hoàn thành tất cả bài tập được giao." : "Chưa có bài tập mới. Hãy khám phá kho mô phỏng cộng đồng.";
  const stats = [
    { label: "Cần hoàn thành", value: pendingAssignments },
    { label: "Đã nộp", value: completedAssignments },
    { label: "Lớp học", value: loading ? "—" : classes.length },
    { label: "Mô phỏng cộng đồng", value: sharedResources },
  ];
  return <motion.div initial={reducedMotion ? false : { opacity: 0, y: 8 }} animate={{ opacity: 1, y: 0 }} transition={{ duration: .25 }}>
    <section className="student-hero" aria-label="Tổng quan học tập">
      <DotField gap={22} radius={110} />
      <div className="student-hero-copy">
        {schoolName && <span className="student-hero-school">{schoolName}</span>}
        <h1>Xin chào, <span className="text-shine">{givenName}</span></h1>
        <p>{message}</p>
        <button type="button" onClick={onExplore} className="student-hero-cta"><LearningIcon name="book" />Khám phá cộng đồng</button>
      </div>
      <dl className="student-hero-stats">
        {stats.map(stat => <div key={stat.label}><dt>{stat.label}</dt><dd>{stat.value}</dd></div>)}
      </dl>
    </section>
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