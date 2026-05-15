import Link from "next/link";
import Image from "next/image";

interface BrandLogoProps {
  href?: string;
  className?: string;
}

export function BrandLogo({ href = "/", className = "" }: BrandLogoProps) {
  return (
    <Link href={href} className={`brand-logo ${className}`.trim()} aria-label="VivuPlan">
      <div className="brand-logo-mark">
        <Image
          src="/logo.png"
          alt="VivuPlan Logo"
          width={56}
          height={56}
          className="brand-logo-image"
        />
      </div>
      <span className="brand-logo-text" aria-hidden="true">
        <span className="text-[#20BDB4]">Vivu</span>
        <span className="text-[#062A5C]">Plan</span>
      </span>
    </Link>
  );
}
