import { OverviewView } from "../../components/roles/admin/AdminRoleViews";

export default function AdminConsole() {
  return <OverviewView />;
}

export function AdminPlaceholderView({ title }: Readonly<{ title: string }>) {
  return <div className="admin-content"><section className="admin-empty-panel"><div className="admin-empty-icon"><span>⋯</span></div><h2>{title}</h2><p>This admin route is ready for its PhysLive data source.</p></section></div>;
}
