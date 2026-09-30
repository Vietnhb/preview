import type { User } from "../../../types/physlive";

export type AccountDetails = {
  dateOfBirth: string;
  avatarUrl: string;
  staffType: "TEACHER" | "DEPARTMENT_HEAD";
  reviewerCanEdit: boolean;
  reviewerCanReview: boolean;
};

export const blankAccountDetails: AccountDetails = {
  dateOfBirth: "", avatarUrl: "", staffType: "TEACHER", reviewerCanEdit: true, reviewerCanReview: true,
};

export function accountDetails(user: User): AccountDetails {
  return {
    dateOfBirth: user.dateOfBirth ?? "", avatarUrl: user.avatarUrl ?? "",
    staffType: user.staffType === "DEPARTMENT_HEAD" ? "DEPARTMENT_HEAD" : "TEACHER",
    reviewerCanEdit: user.role === "REVIEWER" ? user.reviewerCanEdit !== false : true,
    reviewerCanReview: user.role === "REVIEWER" ? user.reviewerCanReview !== false : true,
  };
}

export function accountDetailsPayload(details: AccountDetails, role: string) {
  return {
    dateOfBirth: details.dateOfBirth || undefined,
    avatarUrl: details.avatarUrl.trim(),
    ...(role === "STAFF" ? { staffType: details.staffType } : {}),
    ...(role === "REVIEWER" ? { reviewerCanEdit: details.reviewerCanEdit, reviewerCanReview: details.reviewerCanReview } : {}),
  };
}
