import type { ReactNode } from "react";
import { Badge } from "./Badge";

export function SectionHeader({
  eyebrow,
  title,
  description,
  align = "left",
  action,
}: {
  eyebrow?: string;
  title: ReactNode;
  description?: string;
  align?: "left" | "center";
  action?: ReactNode;
}) {
  return (
    <div className={`section-header section-header-${align}`}>
      <div>
        {eyebrow && (
          <Badge tone="teal" className="section-eyebrow">
            {eyebrow}
          </Badge>
        )}
        <h2>{title}</h2>
        {description && <p>{description}</p>}
      </div>
      {action && <div className="section-action">{action}</div>}
    </div>
  );
}

