import React from "react";
import "./FrottoBadge.css";

export type FrottoBadgeVariant =
  | "success"
  | "warning"
  | "danger"
  | "info"
  | "neutral"
  | "dark";

export interface FrottoBadgeProps {
  variant?: FrottoBadgeVariant;
  className?: string;
  children: React.ReactNode;
}

const FrottoBadge: React.FC<FrottoBadgeProps> = ({
  variant = "neutral",
  className,
  children,
}) => {
  const classes = ["frotto-badge", `frotto-badge--${variant}`, className]
    .filter(Boolean)
    .join(" ");

  return <span className={classes}>{children}</span>;
};

export default FrottoBadge;
