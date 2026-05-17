const API_BASE = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080";

export class ApiError extends Error {
  status: number;

  constructor(message: string, status: number) {
    super(message);
    this.name = "ApiError";
    this.status = status;
  }
}

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
    throw new ApiError(body.error || "Có lỗi xảy ra", res.status);
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
    }).then(handleResponse<AuthResponse>),

  login: (data: { email: string; password: string }) =>
    fetch(`${API_BASE}/api/auth/login`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(data),
    }).then(handleResponse<AuthResponse>),

  google: (data: { idToken: string }) =>
    fetch(`${API_BASE}/api/auth/google`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(data),
    }).then(handleResponse<AuthResponse>),

  me: () =>
    fetch(`${API_BASE}/api/auth/me`, { headers: authHeaders() })
      .then(handleResponse<User>),

  updateProfile: (data: { name: string; avatarUrl?: string }) =>
    fetch(`${API_BASE}/api/auth/me`, {
      method: "PATCH",
      headers: authHeaders(),
      body: JSON.stringify(data),
    }).then(handleResponse<User>),

  changePassword: (data: { currentPassword: string; newPassword: string }) =>
    fetch(`${API_BASE}/api/auth/me/password`, {
      method: "PATCH",
      headers: authHeaders(),
      body: JSON.stringify(data),
    }).then(handleResponse<User>),
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

  addActivity: (tripId: number, dayNumber: number, data: ActivityMutationRequest) =>
    fetch(`${API_BASE}/api/trips/${tripId}/days/${dayNumber}/activities`, {
      method: "POST",
      headers: authHeaders(),
      body: JSON.stringify(data),
    }).then(handleResponse<TripResponse>),

  updateActivity: (tripId: number, activityId: number, data: ActivityMutationRequest) =>
    fetch(`${API_BASE}/api/trips/${tripId}/activities/${activityId}`, {
      method: "PATCH",
      headers: authHeaders(),
      body: JSON.stringify(data),
    }).then(handleResponse<TripResponse>),

  deleteActivity: (tripId: number, activityId: number) =>
    fetch(`${API_BASE}/api/trips/${tripId}/activities/${activityId}`, {
      method: "DELETE",
      headers: authHeaders(),
    }).then(handleResponse<TripResponse>),

  previewRegenerateDay: (tripId: number, dayNumber: number, data: RegenerateDayRequest) =>
    fetch(`${API_BASE}/api/trips/${tripId}/days/${dayNumber}/regenerate-preview`, {
      method: "POST",
      headers: authHeaders(),
      body: JSON.stringify(data),
    }).then(handleResponse<RegenerateDayPreviewResponse>),

  applyRegenerateDay: (tripId: number, dayNumber: number, proposalId: string, selectedActivityIndexes?: number[]) =>
    fetch(`${API_BASE}/api/trips/${tripId}/days/${dayNumber}/regenerate-apply`, {
      method: "POST",
      headers: authHeaders(),
      body: JSON.stringify({ proposalId, selectedActivityIndexes }),
    }).then(handleResponse<TripResponse>),

  publicTrips: (page = 0, size = 12) =>
    fetch(`${API_BASE}/api/trips/public?page=${page}&size=${size}`)
      .then(handleResponse<{ content: TripResponse[]; totalElements: number }>),

  getByShareCode: (code: string) =>
    fetch(`${API_BASE}/api/trips/public/share/${code}`)
      .then(handleResponse<TripResponse>),
};

// ─── Destination API ─────────────────────────────────────────────────────────

export const destinationApi = {
  list: (params: { q?: string; region?: string; featured?: boolean } = {}) => {
    const query = new URLSearchParams();
    if (params.q) query.set("q", params.q);
    if (params.region) query.set("region", params.region);
    if (params.featured !== undefined) query.set("featured", String(params.featured));
    const suffix = query.toString() ? `?${query}` : "";
    return fetch(`${API_BASE}/api/destinations${suffix}`).then(handleResponse<DestinationResponse[]>);
  },

  featured: () =>
    fetch(`${API_BASE}/api/destinations/featured`)
      .then(handleResponse<DestinationResponse[]>),

  getBySlug: (slug: string) =>
    fetch(`${API_BASE}/api/destinations/${slug}`)
      .then(handleResponse<DestinationResponse>),
};

// ─── Types ────────────────────────────────────────────────────────────────────

export interface User {
  id: number;
  name: string;
  email: string;
  avatarUrl?: string;
  role: string;
  roles?: string[];
  provider: "LOCAL" | "GOOGLE";
}

export interface AuthResponse {
  token: string;
  user: User;
}

export interface GenerateRequest {
  destination: string;
  departure: string;
  startDate?: string;
  endDate?: string;
  days: number;
  budgetPerPerson: number;
  budgetTotal?: number;
  budgetMode?: string;
  travelerCount?: number;
  style: string;
  groupType: string;
  transport: string;
  outboundTransport?: string;
  localTransport?: string;
  destinationSuggested?: boolean;
  mustVisit?: string;
  avoid?: string;
  notes?: string;
}

export interface TripResponse {
  id: number;
  destination: string;
  departure?: string;
  startDate?: string;
  endDate?: string;
  days: number;
  budgetPerPerson: number;
  budgetTotal?: number;
  budgetMode?: string;
  travelerCount?: number;
  style: string;
  groupType: string;
  transport: string;
  outboundTransport?: string;
  localTransport?: string;
  destinationSuggested?: boolean;
  mustVisit?: string;
  avoid?: string;
  status: string;
  isPublic: boolean;
  shareCode: string;
  viewCount: number;
  schedule?: DayResponse[];
  budget?: BudgetBreakdown;
  warnings?: string[];
  requestFulfillment?: RequestFulfillment;
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
  googlePlaceId?: string;
  sortOrder: number;
}

export interface ActivityMutationRequest {
  time: string;
  name: string;
  type?: string;
  location?: string;
  duration?: string;
  estimatedCost?: number;
  note?: string;
  latitude?: number;
  longitude?: number;
  googlePlaceId?: string;
  sortOrder?: number;
}

export interface RegenerateDayRequest {
  intent: "REGENERATE" | "LIGHTER" | "CHEAPER" | "MORE_LOCAL" | "ADD_TRANSPORT" | "OPTIMIZE_TIME";
  instruction?: string;
}

export interface RegenerateDayPreviewResponse {
  proposalId: string;
  dayNumber: number;
  day: DayResponse;
  oldBudget: number;
  newBudget: number;
  warnings: string[];
  requestFulfillment?: RequestFulfillment;
}

export interface RequestFulfillment {
  overallStatus?: "FULFILLED" | "PARTIAL" | "NOT_FULFILLED" | "UNCLEAR" | "NO_REQUEST" | string;
  items?: RequestFulfillmentItem[];
}

export interface RequestFulfillmentItem {
  requestedText?: string;
  status?: "FULFILLED" | "PARTIAL" | "NOT_APPLIED" | "UNCLEAR" | string;
  reasonCode?: "APPLIED" | "WEATHER_SAFETY" | "BUDGET" | "TIME_CONFLICT" | "DUPLICATE" | "CONSTRAINT" | "UNCLEAR" | "OTHER" | string;
  userMessage?: string;
}

export interface BudgetBreakdown {
  total: number;
  transport: number;
  accommodation: number;
  food: number;
  activities: number;
}

export interface DestinationResponse {
  id: number;
  name: string;
  slug: string;
  region: "Miền Bắc" | "Miền Trung" | "Miền Nam";
  tourismRegion?: string;
  province?: string;
  category: string;
  tag: string;
  recommendedDays: string;
  rating: number;
  tripCount: number;
  imageUrl: string;
  summary: string;
  description?: string;
  bestTimeToVisit?: string;
  estimatedBudgetMin?: number;
  estimatedBudgetMax?: number;
  latitude?: number;
  longitude?: number;
  tags: string[];
  featured: boolean;
  sourceName?: string;
  sourceUrl?: string;
}
