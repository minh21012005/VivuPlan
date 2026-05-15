import Link from "next/link";
import { Home, LogIn, Compass } from "lucide-react";

interface ErrorPageProps {
  code: 401 | 403;
  title: string;
  description: string;
  primaryAction: { label: string; href: string; icon: "login" | "home" };
}

export function ErrorPage({ code, title, description, primaryAction }: ErrorPageProps) {
  const codeColors: Record<number, string> = {
    401: "linear-gradient(135deg, #f59e0b, #ef4444)",
    403: "linear-gradient(135deg, #ef4444, #dc2626)",
  };

  const gradient = codeColors[code];

  return (
    <div style={{
      minHeight: "100vh",
      background: "var(--bg)",
      display: "flex",
      flexDirection: "column",
    }}>
      <main style={{
        flex: 1,
        display: "flex",
        flexDirection: "column",
        alignItems: "center",
        justifyContent: "center",
        padding: "60px 24px",
        textAlign: "center",
      }}>
        {/* Giant status code */}
        <div style={{
          fontSize: "clamp(100px, 20vw, 200px)",
          fontWeight: 900,
          fontFamily: "var(--font-heading)",
          lineHeight: 1,
          background: gradient,
          WebkitBackgroundClip: "text",
          WebkitTextFillColor: "transparent",
          backgroundClip: "text",
          marginBottom: "8px",
        }}>
          {code}
        </div>

        <h1 style={{
          fontFamily: "var(--font-heading)",
          fontSize: "clamp(22px, 3vw, 32px)",
          fontWeight: 800,
          color: "var(--text)",
          marginBottom: "16px",
        }}>
          {title}
        </h1>

        <p style={{
          fontSize: "16px",
          color: "var(--text-3)",
          maxWidth: "420px",
          lineHeight: 1.7,
          marginBottom: "40px",
        }}>
          {description}
        </p>

        <div style={{ display: "flex", gap: "12px", flexWrap: "wrap", justifyContent: "center" }}>
          <Link href={primaryAction.href} className="btn btn-primary" style={{ textDecoration: "none" }}>
            {primaryAction.icon === "login" ? <LogIn size={16} /> : <Home size={16} />}
            {primaryAction.label}
          </Link>
          <Link href="/" className="btn btn-secondary" style={{ textDecoration: "none" }}>
            <Compass size={16} /> Trang chủ
          </Link>
        </div>
      </main>
    </div>
  );
}
