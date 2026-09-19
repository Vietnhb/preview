import { useEffect, useState } from "react";
import { useLocation } from "react-router-dom";
import { getLicenseStatus, type LicenseStatus } from "../api/userApi";
import { usePhysliveStore } from "../store/usePhysliveStore";
export default function LicenseNotice() {
  const user = usePhysliveStore(state => state.user);
  const { pathname } = useLocation();
  const [license, setLicense] = useState<LicenseStatus | null>(null);
  const [dismissedLicenseNoticeKey, setDismissedLicenseNoticeKey] = useState("");
  useEffect(() => {
    if (!user?.schoolId) return;
    let active = true;
    void getLicenseStatus().then(value => { if (active) setLicense(value); }).catch(() => { if (active) setLicense(null); });
    return () => { active = false; };
  }, [user?.id, user?.schoolId, pathname]);
  const effectiveLicense = user?.schoolId ? license : null;
  const licenseNoticeKey = effectiveLicense
    ? `${user?.id}:${user?.schoolId}:${effectiveLicense.canPerformWriteOperations ? `renewal:${effectiveLicense.daysUntilExpiry}` : "inactive"}`
    : "";

  return <>      {user?.schoolId && effectiveLicense && (!effectiveLicense.canPerformWriteOperations || effectiveLicense.showRenewalBanner) && licenseNoticeKey !== dismissedLicenseNoticeKey &&
        <div role="status" className="license-notice">
          <p>
            {!effectiveLicense.canPerformWriteOperations
              ? "Trường chưa có license hiệu lực. Bạn có thể xem dữ liệu; vui lòng liên hệ quản lý trường để gia hạn."
              : `License của trường còn ${effectiveLicense.daysUntilExpiry} ngày.`}
          </p>
          <button
            type="button"
            aria-label="Đóng thông báo license"
            title="Đóng thông báo"
            onClick={() => setDismissedLicenseNoticeKey(licenseNoticeKey)}
          >
            <svg aria-hidden="true" viewBox="0 0 16 16" focusable="false">
              <path d="M3.5 3.5l9 9m0-9-9 9" />
            </svg>
          </button>
        </div>}
</>;
}
