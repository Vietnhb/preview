import { Link, useLocation } from "react-router-dom";
import { Avatar, Button, DropdownMenu, Flex, IconButton, TabNav } from "@radix-ui/themes";
import { ExitIcon, HamburgerMenuIcon, PersonIcon } from "@radix-ui/react-icons";
import LearningIcon from "./LearningIcon";
import type { User } from "../../types/physlive";
import { roleHome } from "../../types/roles";
import styles from "./RoleNavigation.module.css";

type RoleLink = { to: string; label: string; icon: Parameters<typeof LearningIcon>[0]["name"] };

export default function RoleNavigation({ user, items, onLogout }: { user: User; items: RoleLink[]; onLogout: () => void }) {
  const { pathname } = useLocation();
  return <nav className={styles.nav} aria-label="Điều hướng chính">
    <div className={styles.inner}>
      <Link to={roleHome(user.role)} className={styles.brand}><img src="/favicon.ico" alt="" /><span>PhysLive</span></Link>
      <TabNav.Root className={styles.links}>
        {items.map(item => <TabNav.Link asChild key={item.to} active={pathname === item.to || pathname.startsWith(item.to + "/")}><Link to={item.to}><LearningIcon name={item.icon} />{item.label}</Link></TabNav.Link>)}
      </TabNav.Root>
      <Flex gap="3" align="center" className={styles.account}>
        <Button asChild variant="ghost" color="gray" className={styles.profile}><Link to="/profile"><Avatar size="2" src={user.avatarUrl || undefined} fallback={user.fullName?.slice(0, 1) || "U"} /><span>{user.fullName}</span></Link></Button>
        <IconButton variant="soft" color="gray" aria-label="Đăng xuất" onClick={onLogout}><ExitIcon /></IconButton>
      </Flex>
      <DropdownMenu.Root><DropdownMenu.Trigger><IconButton variant="soft" className={styles.mobile} aria-label="Mở menu điều hướng"><HamburgerMenuIcon /></IconButton></DropdownMenu.Trigger><DropdownMenu.Content align="end">
        {items.map(item => <DropdownMenu.Item key={item.to} asChild><Link to={item.to}><LearningIcon name={item.icon} />{item.label}</Link></DropdownMenu.Item>)}
        <DropdownMenu.Separator /><DropdownMenu.Item asChild><Link to="/profile"><PersonIcon />Hồ sơ cá nhân</Link></DropdownMenu.Item><DropdownMenu.Item color="red" onSelect={onLogout}><ExitIcon />Đăng xuất</DropdownMenu.Item>
      </DropdownMenu.Content></DropdownMenu.Root>
    </div>
  </nav>;
}
