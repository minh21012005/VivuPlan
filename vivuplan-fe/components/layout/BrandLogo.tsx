import Link from "next/link";

interface BrandLogoProps {
  href?: string;
  className?: string;
}

export function BrandLogo({ href = "/", className = "" }: BrandLogoProps) {
  return (
    <Link href={href} className={`brand-logo ${className}`.trim()} aria-label="VivuPlan">
      <img className="brand-logo-image" src="/a.png" alt="VivuPlan" />
    </Link>
  );
}
