const API_BASE = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080";

// ─── Auth helpers ────────────────────────────────────────────────────────────

function getToken(): string | null {
  if (typeof window === "undefined") return null;
  return localStorage.getItem("vp_token");
}

function authHeaders(): HeadersInit {
  const token = getToken();
  return {
    "Content-Type": "application/json",
    ...(token ? { Authorization: `Bearer ${token}` } : {}),
  };
}

async function handleResponse<T>(res: Response): Promise<T> {
  if (!res.ok) {
    const body = await res.json().catch(() => ({ error: res.statusText }));
    throw new Error(body.error || "Có lỗi xảy ra");
  }
  return res.json();
}

// ─── Auth API ─────────────────────────────────────────────────────────────────

export const authApi = {
  register: (data: { name: string; email: string; password: string }) =>
    fetch(`${API_BASE}/api/auth/register`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(data),
    }).then(handleResponse<{ token: string; user: User }>),

  login: (data: { email: string; password: string }) =>
    fetch(`${API_BASE}/api/auth/login`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(data),
    }).then(handleResponse<{ token: string; user: User }>),

  me: () =>
    fetch(`${API_BASE}/api/auth/me`, { headers: authHeaders() })
      .then(handleResponse<User>),
};

// ─── Trip API ─────────────────────────────────────────────────────────────────

export const tripApi = {
  generate: (data: GenerateRequest) =>
    fetch(`${API_BASE}/api/trips/generate`, {
      method: "POST",
      headers: authHeaders(),
      body: JSON.stringify(data),
    }).then(handleResponse<TripResponse>),

  myTrips: () =>
    fetch(`${API_BASE}/api/trips`, { headers: authHeaders() })
      .then(handleResponse<TripResponse[]>),

  getTrip: (id: string | number) =>
    fetch(`${API_BASE}/api/trips/${id}`, { headers: authHeaders() })
      .then(handleResponse<TripResponse>),

  deleteTrip: (id: number) =>
    fetch(`${API_BASE}/api/trips/${id}`, {
      method: "DELETE",
      headers: authHeaders(),
    }).then(handleResponse<{ message: string }>),

  toggleVisibility: (id: number) =>
    fetch(`${API_BASE}/api/trips/${id}/visibility`, {
      method: "PATCH",
      headers: authHeaders(),
    }).then(handleResponse<TripResponse>),

  updateStatus: (id: number, status: string) =>
    fetch(`${API_BASE}/api/trips/${id}/status`, {
      method: "PATCH",
      headers: authHeaders(),
      body: JSON.stringify({ status }),
    }).then(handleResponse<TripResponse>),

  publicTrips: (page = 0, size = 12) =>
    fetch(`${API_BASE}/api/trips/public?page=${page}&size=${size}`)
      .then(handleResponse<{ content: TripResponse[]; totalElements: number }>),

  getByShareCode: (code: string) =>
    fetch(`${API_BASE}/api/trips/public/share/${code}`)
      .then(handleResponse<TripResponse>),
};

// ─── Types ────────────────────────────────────────────────────────────────────

export interface User {
  id: number;
  name: string;
  email: string;
  avatarUrl?: string;
  role: string;
}

export interface GenerateRequest {
  destination: string;
  departure: string;
  days: number;
  budgetPerPerson: number;
  style: string;
  groupType: string;
  transport: string;
  notes?: string;
}

export interface TripResponse {
  id: number;
  destination: string;
  departure?: string;
  days: number;
  budgetPerPerson: number;
  style: string;
  groupType: string;
  transport: string;
  status: string;
  isPublic: boolean;
  shareCode: string;
  viewCount: number;
  schedule?: DayResponse[];
  budget?: BudgetBreakdown;
  createdAt: string;
}

export interface DayResponse {
  day: number;
  title: string;
  summary: string;
  activities: ActivityResponse[];
}

export interface ActivityResponse {
  id: number;
  time: string;
  name: string;
  type: string;
  location: string;
  duration: string;
  estimatedCost: number;
  note: string;
  rating: number;
  latitude?: number;
  longitude?: number;
  sortOrder: number;
}

export interface BudgetBreakdown {
  total: number;
  transport: number;
  accommodation: number;
  food: number;
  activities: number;
}
