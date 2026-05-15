"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import Image from "next/image";
import Link from "next/link";
import Navbar from "@/components/layout/Navbar";
import Footer from "@/components/layout/Footer";
import { authApi, type User } from "@/lib/api";
import { useAuth } from "@/hooks/useAuth";
import {
  ArrowLeft,
  CheckCircle2,
  Eye,
  EyeOff,
  KeyRound,
  Save,
  User as UserIcon,
  X,
} from "lucide-react";

// ─── Toast ────────────────────────────────────────────────────────────────────

function Toast({ message, tone }: { message: string; tone: "success" | "error" }) {
  return (
    <div className={`settings-toast settings-toast-${tone}`}>
      {tone === "success" && <CheckCircle2 size={15} />}
      {message}
    </div>
  );
}

// ─── Password field ──────────────────────────────────────────────────────────

function PasswordInput({
  id,
  label,
  value,
  onChange,
  disabled,
}: {
  id: string;
  label: string;
  value: string;
  onChange: (v: string) => void;
  disabled?: boolean;
}) {
  const [show, setShow] = useState(false);
  return (
    <div className="settings-field-group">
      <label htmlFor={id} className="settings-field-label">{label}</label>
      <div className="settings-input-wrap">
        <input
          id={id}
          type={show ? "text" : "password"}
          className="settings-input-premium"
          value={value}
          onChange={(e) => onChange(e.target.value)}
          disabled={disabled}
          autoComplete="off"
        />
        <button
          type="button"
          className="settings-eye-btn"
          onClick={() => setShow((s) => !s)}
          tabIndex={-1}
          aria-label={show ? "Ẩn mật khẩu" : "Hiện mật khẩu"}
        >
          {show ? <EyeOff size={16} /> : <Eye size={16} />}
        </button>
      </div>
    </div>
  );
}

// ─── Password Modal ─────────────────────────────────────────────────────────

function PasswordModal({
  isOpen,
  onClose,
  onSuccess,
}: {
  isOpen: boolean;
  onClose: () => void;
  onSuccess: (msg: string) => void;
}) {
  const [currentPassword, setCurrentPassword] = useState("");
  const [newPassword, setNewPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  if (!isOpen) return null;

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);

    if (newPassword !== confirmPassword) {
      setError("Xác nhận mật khẩu không khớp.");
      return;
    }
    if (newPassword.length < 8) {
      setError("Mật khẩu mới tối thiểu 8 ký tự.");
      return;
    }

    setSaving(true);
    try {
      await authApi.changePassword({ currentPassword, newPassword });
      onSuccess("Đổi mật khẩu thành công!");
      onClose();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Đổi mật khẩu thất bại.");
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="settings-modal-backdrop" onClick={(e) => e.target === e.currentTarget && onClose()}>
      <div className="settings-modal-panel">
        <div className="settings-modal-header">
          <div className="settings-modal-title">
            <KeyRound size={18} />
            <h3>Đổi mật khẩu</h3>
          </div>
          <button type="button" className="settings-modal-close" onClick={onClose}>
            <X size={20} />
          </button>
        </div>

        <form onSubmit={handleSubmit} className="settings-form" noValidate>
          <div style={{ display: 'flex', flexDirection: 'column', gap: '18px', padding: '24px' }}>
            <PasswordInput
              id="modal-current-password"
              label="Mật khẩu hiện tại"
              value={currentPassword}
              onChange={setCurrentPassword}
              disabled={saving}
            />
            <PasswordInput
              id="modal-new-password"
              label="Mật khẩu mới"
              value={newPassword}
              onChange={setNewPassword}
              disabled={saving}
            />
            <PasswordInput
              id="modal-confirm-password"
              label="Xác nhận mật khẩu mới"
              value={confirmPassword}
              onChange={setConfirmPassword}
              disabled={saving}
            />
          </div>

          {error && <div className="settings-modal-error">{error}</div>}

          <div className="settings-modal-footer">
            <button type="button" className="btn btn-secondary" onClick={onClose} disabled={saving}>
              Hủy bỏ
            </button>
            <button
              type="submit"
              className="btn btn-primary"
              disabled={saving || !currentPassword || !newPassword || !confirmPassword}
            >
              {saving ? <span className="spinner spinner-inline spinner-on-primary" /> : <Save size={15} />}
              {saving ? "Đang lưu..." : "Cập nhật mật khẩu"}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}

// ─── Page ─────────────────────────────────────────────────────────────────────

export default function SettingsPage() {
  const router = useRouter();
  const { user, loading: authLoading, updateUser } = useAuth();

  // Profile form
  const [name, setName] = useState("");
  const [profileSaving, setProfileSaving] = useState(false);
  const [isPassModalOpen, setIsPassModalOpen] = useState(false);
  const [toast, setToast] = useState<{ message: string; tone: "success" | "error" } | null>(null);

  // Redirect if not logged in or if it's a Google user
  useEffect(() => {
    if (!authLoading) {
      if (!user) {
        router.push("/login");
      } else if (user.provider === "GOOGLE") {
        router.push("/dashboard");
      }
    }
  }, [authLoading, user, router]);

  // Populate form
  useEffect(() => {
    if (user) setName(user.name ?? "");
  }, [user]);

  // Auto-clear toasts
  useEffect(() => {
    if (!toast) return;
    const t = window.setTimeout(() => setToast(null), 3500);
    return () => window.clearTimeout(t);
  }, [toast]);

  const handleProfileSave = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!name.trim()) return;
    setProfileSaving(true);
    try {
      const updated = await authApi.updateProfile({ name: name.trim() });
      updateUser(updated as User);
      setToast({ message: "Đã lưu thông tin thành công!", tone: "success" });
    } catch (err) {
      setToast({
        message: err instanceof Error ? err.message : "Lưu thất bại, vui lòng thử lại.",
        tone: "error",
      });
    } finally {
      setProfileSaving(false);
    }
  };

  if (authLoading) {
    return (
      <div className="settings-page">
        <Navbar />
        <div className="settings-loading"><div className="spinner" /></div>
      </div>
    );
  }

  if (!user) return null;

  const isGoogle = user.provider === "GOOGLE";
  const avatarLetter = user.name?.charAt(0).toUpperCase() ?? "?";

  return (
    <div className="settings-page">
      <Navbar />

      {/* Toast notifications (Fixed top-right) */}
      {toast && <Toast message={toast.message} tone={toast.tone} />}

      {/* Hero */}
      <section className="settings-hero">
        <div className="container">
          <Link href="/dashboard" className="settings-back-link">
            <ArrowLeft size={16} /> Quay lại thư viện
          </Link>
          <h1>Cài đặt tài khoản</h1>
          <p>Quản lý thông tin cá nhân và bảo mật tài khoản của bạn.</p>
        </div>
      </section>

      <main className="container settings-main">

        {/* Main Settings Section */}
        <section className="settings-card">
          <div className="settings-card-header" style={{ justifyContent: 'space-between' }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
              <UserIcon size={18} />
              <h2>Thông tin tài khoản</h2>
            </div>

            {!isGoogle && (
              <button
                type="button"
                className="btn btn-secondary btn-sm"
                style={{ gap: '8px' }}
                onClick={() => setIsPassModalOpen(true)}
              >
                <KeyRound size={14} /> Đổi mật khẩu
              </button>
            )}
          </div>
          
          <div className="settings-content">
            <form onSubmit={handleProfileSave} className="settings-form-grid" noValidate>
              
              <div className="settings-field-group">
                <label htmlFor="field-name" className="settings-field-label">Tên hiển thị</label>
                <input
                  id="field-name"
                  type="text"
                  className="settings-input-premium"
                  value={name}
                  onChange={(e) => setName(e.target.value)}
                  disabled={profileSaving}
                  placeholder="Nhập tên hiển thị"
                />
              </div>

              <div className="settings-field-group">
                <label className="settings-field-label">Địa chỉ Email</label>
                <input
                  type="email"
                  className="settings-input-premium"
                  value={user.email}
                  disabled
                  readOnly
                />
              </div>

              <div className="settings-footer">
                <button
                  type="submit"
                  className="btn btn-primary btn-save"
                  disabled={profileSaving || !name.trim()}
                  aria-busy={profileSaving}
                >
                  {profileSaving
                    ? <span className="spinner spinner-inline spinner-on-primary" />
                    : <Save size={16} />}
                  {profileSaving ? "Đang lưu..." : "Lưu thay đổi"}
                </button>
              </div>
            </form>
          </div>
        </section>
      </main>

      {/* Password Update Modal */}
      <PasswordModal
        isOpen={isPassModalOpen}
        onClose={() => setIsPassModalOpen(false)}
        onSuccess={(msg) => {
          setToast({ message: msg, tone: "success" });
        }}
      />

      <Footer />
    </div>
  );
}
