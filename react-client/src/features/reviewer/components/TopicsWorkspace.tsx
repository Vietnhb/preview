import { Heading, Tabs, Text } from "@radix-ui/themes";
import { VersionsTab } from "./VersionsTab";
import { ModuleApprovalTab } from "./ModuleApprovalTab";

const SECTIONS = [
  { id: "schemas", label: "Chủ đề", hint: "Mỗi chủ đề khai báo đối tượng, đại lượng và đơn vị mà AI được phép dùng khi đọc đề bài." },
  { id: "solvers", label: "Bộ giải", hint: "Bộ giải tính kết quả mô phỏng; bộ giải đối chiếu kiểm tra lại bằng công thức chính xác." },
  { id: "modules", label: "Gói phát hành", hint: "Gói ghép chủ đề + bộ giải đã duyệt để giáo viên sử dụng." },
];

/** One home for the physics catalogue: topic → solver → release, in the order they depend on each other. */
export function TopicsWorkspace({ section, onSection }: Readonly<{ section: string | null; onSection: (section: string) => void }>) {
  const active = SECTIONS.some(item => item.id === section) ? section as string : "schemas";
  const current = SECTIONS.find(item => item.id === active)!;
  return <div className="reviewer-stack">
    <header className="reviewer-page-head"><div><Heading as="h1" size="6">Chủ đề vật lý</Heading><Text as="p" size="2" color="gray" mt="1">Quy trình: tạo bản nháp → phê duyệt → phát hành. Phiên bản đã phê duyệt không sửa trực tiếp, chỉ tạo phiên bản mới.</Text></div></header>
    <Tabs.Root value={active} onValueChange={onSection}>
      <Tabs.List size="2">{SECTIONS.map((item, index) => <Tabs.Trigger key={item.id} value={item.id}><span className="reviewer-step-number">{index + 1}</span>{item.label}</Tabs.Trigger>)}</Tabs.List>
    </Tabs.Root>
    <Text as="p" size="2" color="gray" className="reviewer-section-hint">{current.hint}</Text>
    {active === "schemas" && <VersionsTab key="schemas" solver={false} />}
    {active === "solvers" && <VersionsTab key="solvers" solver />}
    {active === "modules" && <ModuleApprovalTab />}
  </div>;
}
