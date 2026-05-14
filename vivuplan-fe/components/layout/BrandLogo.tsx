import Link from "next/link";

interface BrandLogoProps {
  href?: string;
  className?: string;
}

export function BrandLogo({ href = "/", className = "" }: BrandLogoProps) {
  return (
    <Link href={href} className={`flex items-center gap-1 group ${className}`.trim()} aria-label="VivuPlan">
      <div className="flex items-center justify-center w-14 h-14 overflow-hidden shrink-0">
        <img
          src="/b.jpg"
          alt="VivuPlan Logo"
          className="w-full h-full object-contain mix-blend-multiply contrast-125 brightness-110 scale-[2.2] origin-center"
        />
      </div>
      <span className="text-[25px] font-extrabold tracking-tight" aria-hidden="true">
        <span className="text-[var(--primary)]">Vivu</span>
        <span className="text-[var(--secondary)]">Plan</span>
      </span>
    </Link>
  );
}
