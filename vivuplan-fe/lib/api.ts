const API_BASE = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080";

export class ApiError extends Error {
  status: number;
  code?: string;

  constructor(message: string, status: number, code?: string) {
    super(message);
    this.name = "ApiError";
    this.status = status;
    this.code = code;
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
    const detailMessage = body.details && typeof body.details === "object"
      ? Object.values(body.details).find((message) => typeof message === "string" && message.trim().length > 0)
      : undefined;
    throw new ApiError(
      (typeof detailMessage === "string" ? detailMessage : undefined) || body.error || "Có lỗi xảy ra",
      res.status,
      body.code,
    );
  }
  return res.json();
}

// ─── Auth API ─────────────────────────────────────────────────────────────────

export const authApi = {
  requestRegisterOtp: (data: { name: string; email: string; password: string }) =>
    fetch(`${API_BASE}/api/auth/register/request-otp`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(data),
    }).then(handleResponse<RegisterOtpResponse>),

  verifyRegisterOtp: (data: { email: string; otp: string }) =>
    fetch(`${API_BASE}/api/auth/register/verify`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(data),
    }).then(handleResponse<AuthResponse>),

  requestPasswordResetOtp: (data: { email: string }) =>
    fetch(`${API_BASE}/api/auth/password/forgot/request-otp`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(data),
    }).then(handleResponse<ForgotPasswordOtpResponse>),

  resetPasswordWithOtp: (data: { email: string; otp: string; newPassword: string }) =>
    fetch(`${API_BASE}/api/auth/password/forgot/verify`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(data),
    }).then(handleResponse<{ message: string }>),

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

  suggestDestinations: (data: DestinationSuggestionRequest) =>
    fetch(`${API_BASE}/api/trips/destination-suggestions`, {
      method: "POST",
      headers: authHeaders(),
      body: JSON.stringify(data),
    }).then(handleResponse<DestinationSuggestionResponse>),

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

  geocode: (q: string) => {
    const query = new URLSearchParams({ q });
    return fetch(`${API_BASE}/api/destinations/geocode?${query}`)
      .then(handleResponse<LatLonResponse>);
  },

  weather: (params: { destination?: string; lat?: number; lon?: number }) => {
    const query = new URLSearchParams();
    if (params.destination) query.set("destination", params.destination);
    if (params.lat !== undefined) query.set("lat", String(params.lat));
    if (params.lon !== undefined) query.set("lon", String(params.lon));
    const suffix = query.toString() ? `?${query}` : "";
    return fetch(`${API_BASE}/api/destinations/weather${suffix}`)
      .then(handleResponse<WeatherDayResponse[]>);
  },

  currentWeather: (params: { destination?: string; lat?: number; lon?: number }) => {
    const query = new URLSearchParams();
    if (params.destination) query.set("destination", params.destination);
    if (params.lat !== undefined) query.set("lat", String(params.lat));
    if (params.lon !== undefined) query.set("lon", String(params.lon));
    const suffix = query.toString() ? `?${query}` : "";
    return fetch(`${API_BASE}/api/destinations/weather/current${suffix}`)
      .then(handleResponse<CurrentWeatherResponse>);
  },
};

// Billing API

export const billingApi = {
  packages: () =>
    fetch(`${API_BASE}/api/billing/packages`)
      .then(handleResponse<BillingPackage[]>),

  me: () =>
    fetch(`${API_BASE}/api/billing/me`, { headers: authHeaders() })
      .then(handleResponse<BillingMeResponse>),

  createOrder: (packageCode: string) =>
    fetch(`${API_BASE}/api/billing/orders`, {
      method: "POST",
      headers: authHeaders(),
      body: JSON.stringify({ packageCode }),
    }).then(handleResponse<BillingOrder>),

  getOrder: (orderCode: string) =>
    fetch(`${API_BASE}/api/billing/orders/${orderCode}`, { headers: authHeaders() })
      .then(handleResponse<BillingOrder>),

  cancelOrder: (orderCode: string) =>
    fetch(`${API_BASE}/api/billing/orders/${orderCode}/cancel`, {
      method: "POST",
      headers: authHeaders(),
    }).then(handleResponse<BillingOrder>),
};

// Admin API

export const adminApi = {
  stats: () =>
    fetch(`${API_BASE}/api/admin/stats`, { headers: authHeaders() })
      .then(handleResponse<AdminStats>),

  users: (page = 0, size = 20, filters: AdminUserFilters = {}) => {
    const query = adminQuery({ page, size, ...filters });
    return fetch(`${API_BASE}/api/admin/users?${query}`, { headers: authHeaders() })
      .then(handleResponse<PageResponse<AdminUserSummary>>);
  },

  userDetail: (userId: number) =>
    fetch(`${API_BASE}/api/admin/users/${userId}`, { headers: authHeaders() })
      .then(handleResponse<AdminUserDetail>),

  trips: (page = 0, size = 20, filters: AdminTripFilters = {}) => {
    const query = adminQuery({ page, size, ...filters });
    return fetch(`${API_BASE}/api/admin/trips?${query}`, { headers: authHeaders() })
      .then(handleResponse<PageResponse<AdminTripSummary>>);
  },

  tripDetail: (tripId: number) =>
    fetch(`${API_BASE}/api/admin/trips/${tripId}`, { headers: authHeaders() })
      .then(handleResponse<AdminTripDetail>),

  transactions: (page = 0, size = 20, filters: AdminTransactionFilters = {}) => {
    const query = adminQuery({ page, size, ...filters });
    return fetch(`${API_BASE}/api/admin/transactions?${query}`, { headers: authHeaders() })
      .then(handleResponse<PageResponse<AdminTransactionSummary>>);
  },

  updateUserRole: (userId: number, role: "USER" | "ADMIN") =>
    fetch(`${API_BASE}/api/admin/users/${userId}/role`, {
      method: "PATCH",
      headers: authHeaders(),
      body: JSON.stringify({ role }),
    }).then(handleResponse<AdminUserSummary>),

  updateUserLock: (userId: number, locked: boolean) =>
    fetch(`${API_BASE}/api/admin/users/${userId}/lock`, {
      method: "PATCH",
      headers: authHeaders(),
      body: JSON.stringify({ locked }),
    }).then(handleResponse<AdminUserSummary>),
};

function adminQuery(params: Record<string, string | number | undefined>) {
  const query = new URLSearchParams();
  Object.entries(params).forEach(([key, value]) => {
    if (value === undefined || value === "" || value === "ALL") return;
    query.set(key, String(value));
  });
  return query.toString();
}

// ─── Types ────────────────────────────────────────────────────────────────────

export interface User {
  id: number;
  name: string;
  email: string;
  avatarUrl?: string;
  role: string;
  roles?: string[];
  provider: "LOCAL" | "GOOGLE";
  accountLocked?: boolean;
}

export interface AuthResponse {
  token: string;
  user: User;
}

export interface RegisterOtpResponse {
  email: string;
  expiresInSeconds: number;
}

export interface ForgotPasswordOtpResponse {
  email: string;
  expiresInSeconds: number;
}

export interface BillingPackage {
  code: "PLAN_1" | "PLAN_3" | "PLAN_10" | string;
  name: string;
  description: string;
  amount: number;
  planCredits: number;
  editCredits: number;
  highlighted?: boolean;
}

export interface BillingWallet {
  planCredits: number;
  editCredits: number;
}

export interface BillingOrder {
  orderCode: string;
  packageCode: string;
  amount: number;
  planCredits: number;
  editCredits: number;
  status: "PENDING" | "PAID" | "UNDERPAID" | "EXPIRED" | "CANCELLED";
  qrUrl?: string;
  expiresAt: string;
  paidAt?: string;
  paidAmount?: number;
}

export interface BillingMeResponse {
  wallet: BillingWallet;
  recentOrders: BillingOrder[];
}

export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
  first?: boolean;
  last?: boolean;
}

export interface AdminStats {
  totalUsers: number;
  adminUsers: number;
  totalTrips: number;
  publicTrips: number;
  draftTrips: number;
  plannedTrips: number;
  completedTrips: number;
  paidOrders: number;
  totalRevenue: number;
}

export interface AdminUserFilters {
  q?: string;
  role?: "ALL" | "USER" | "ADMIN";
  provider?: "ALL" | "LOCAL" | "GOOGLE";
}

export interface AdminTripFilters {
  q?: string;
  status?: "ALL" | "DRAFT" | "PLANNED" | "COMPLETED";
  visibility?: "ALL" | "PUBLIC" | "PRIVATE";
}

export interface AdminTransactionFilters {
  q?: string;
  status?: "ALL" | "PENDING" | "PAID" | "UNDERPAID" | "EXPIRED" | "CANCELLED";
}

export type AdminTransactionStatus = "PENDING" | "PAID" | "UNDERPAID" | "EXPIRED" | "CANCELLED";

export interface AdminUserSummary {
  id: number;
  name: string;
  email: string;
  avatarUrl?: string;
  role: "USER" | "ADMIN" | string;
  roles: string[];
  provider: "LOCAL" | "GOOGLE";
  emailVerified: boolean;
  accountLocked: boolean;
  createdAt?: string;
}

export interface AdminUserDetail {
  user: AdminUserSummary;
  wallet: BillingWallet;
  totalTrips: number;
  paidOrders: number;
  totalPaid: number;
  recentTrips: AdminTripSummary[];
  recentOrders: AdminTransactionSummary[];
}

export interface AdminTripSummary {
  id: number;
  userId: number;
  userEmail: string;
  departure?: string;
  destination: string;
  days: number;
  status: string;
  isPublic: boolean;
  viewCount: number;
  createdAt?: string;
}

export interface AdminTripDetail {
  trip: TripResponse;
  user: AdminUserSummary;
}

export interface AdminTransactionSummary {
  id: number;
  orderCode: string;
  userId: number;
  userEmail: string;
  packageCode: string;
  amount: number;
  paidAmount?: number;
  planCredits: number;
  editCredits: number;
  status: AdminTransactionStatus | string;
  createdAt?: string;
  paidAt?: string;
  expiresAt?: string;
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

export interface DestinationSuggestionRequest {
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
  mustVisit?: string;
  avoid?: string;
  notes?: string;
}

export interface DestinationSuggestion {
  name: string;
  region: string;
  reason: string;
  budgetFit: string;
  durationFit: string;
  styleFit: string;
  fromCatalog: boolean;
}

export interface DestinationSuggestionResponse {
  suggestions: DestinationSuggestion[];
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
  notes?: string;
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
  costEstimateStatus?: string;
  costEstimateMessage?: string;
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

export interface LatLonResponse {
  lat: number;
  lon: number;
}

export interface WeatherDayResponse {
  date: string;
  code: number;
  maxTemp: number;
  minTemp: number;
  precipitationMm: number;
  precipitationProbability: number;
  windspeedKmh: number;
  outdoorRiskLevel?: number;
  timeWindows?: WeatherWindowResponse[];
}

export interface CurrentWeatherResponse {
  time: string;
  code: number;
  temperatureC: number;
  precipitationMm: number;
  precipitationProbability: number;
  windspeedKmh: number;
  outdoorRiskLevel?: number;
}

export interface WeatherWindowResponse {
  label: string;
  startHour: number;
  endHour: number;
  code: number;
  temperatureC?: number;
  precipitationMm: number;
  precipitationProbability: number;
  windspeedKmh: number;
  outdoorRiskLevel?: number;
}
