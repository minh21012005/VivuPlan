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

type RawTripResponse = Omit<TripResponse, "isPublic"> & {
  isPublic?: boolean;
  public?: boolean;
};

type RawAdminTripDetail = Omit<AdminTripDetail, "trip"> & {
  trip: RawTripResponse;
};

function normalizeTripResponse(trip: RawTripResponse): TripResponse {
  const { public: legacyPublic, ...rest } = trip;
  return {
    ...rest,
    isPublic: trip.isPublic ?? legacyPublic ?? false,
  };
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
    }).then(handleResponse<RawTripResponse>).then(normalizeTripResponse),

  suggestDestinations: (data: DestinationSuggestionRequest) =>
    fetch(`${API_BASE}/api/trips/destination-suggestions`, {
      method: "POST",
      headers: authHeaders(),
      body: JSON.stringify(data),
    }).then(handleResponse<DestinationSuggestionResponse>),

  myTrips: () =>
    fetch(`${API_BASE}/api/trips`, { headers: authHeaders() })
      .then(handleResponse<RawTripResponse[]>)
      .then((trips) => trips.map(normalizeTripResponse)),

  getTrip: (id: string | number) =>
    fetch(`${API_BASE}/api/trips/${id}`, { headers: authHeaders() })
      .then(handleResponse<RawTripResponse>)
      .then(normalizeTripResponse),

  deleteTrip: (id: number) =>
    fetch(`${API_BASE}/api/trips/${id}`, {
      method: "DELETE",
      headers: authHeaders(),
    }).then(handleResponse<{ message: string }>),

  ensurePublicShare: (id: number) =>
    fetch(`${API_BASE}/api/trips/${id}/visibility`, {
      method: "PATCH",
      headers: authHeaders(),
    }).then(handleResponse<RawTripResponse>).then(normalizeTripResponse),

  updateStatus: (id: number, status: string) =>
    fetch(`${API_BASE}/api/trips/${id}/status`, {
      method: "PATCH",
      headers: authHeaders(),
      body: JSON.stringify({ status }),
    }).then(handleResponse<RawTripResponse>).then(normalizeTripResponse),

  addActivity: (tripId: number, dayNumber: number, data: ActivityMutationRequest) =>
    fetch(`${API_BASE}/api/trips/${tripId}/days/${dayNumber}/activities`, {
      method: "POST",
      headers: authHeaders(),
      body: JSON.stringify(data),
    }).then(handleResponse<RawTripResponse>).then(normalizeTripResponse),

  updateActivity: (tripId: number, activityId: number, data: ActivityMutationRequest) =>
    fetch(`${API_BASE}/api/trips/${tripId}/activities/${activityId}`, {
      method: "PATCH",
      headers: authHeaders(),
      body: JSON.stringify(data),
    }).then(handleResponse<RawTripResponse>).then(normalizeTripResponse),

  deleteActivity: (tripId: number, activityId: number) =>
    fetch(`${API_BASE}/api/trips/${tripId}/activities/${activityId}`, {
      method: "DELETE",
      headers: authHeaders(),
    }).then(handleResponse<RawTripResponse>).then(normalizeTripResponse),

  previewRegenerateDay: (tripId: number, dayNumber: number, data: RegenerateDayRequest) =>
    fetch(`${API_BASE}/api/trips/${tripId}/days/${dayNumber}/regenerate-preview`, {
      method: "POST",
      headers: authHeaders(),
      body: JSON.stringify(data),
    }).then(handleResponse<RegenerateDayPreviewResponse>),

  applyRegenerateDay: (
    tripId: number,
    dayNumber: number,
    proposalId: string,
    selectedChangeIds?: string[],
  ) =>
    fetch(`${API_BASE}/api/trips/${tripId}/days/${dayNumber}/regenerate-apply`, {
      method: "POST",
      headers: authHeaders(),
      body: JSON.stringify({ proposalId, selectedChangeIds }),
    }).then(handleResponse<RawTripResponse>).then(normalizeTripResponse),

  getByShareCode: (code: string) =>
    fetch(`${API_BASE}/api/trips/public/share/${code}`)
      .then(handleResponse<RawTripResponse>)
      .then(normalizeTripResponse),
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
      .then(handleResponse<RawAdminTripDetail>)
      .then((response) => ({ ...response, trip: normalizeTripResponse(response.trip) })),

  resolveActivityCoordinates: (tripId: number, dryRun = true) =>
    fetch(`${API_BASE}/api/admin/trips/${tripId}/activity-coordinates/resolve?dryRun=${dryRun}`, {
      method: "POST",
      headers: authHeaders(),
    }).then(handleResponse<AdminActivityCoordinateResolutionResponse>),

  transactions: (page = 0, size = 20, filters: AdminTransactionFilters = {}) => {
    const query = adminQuery({ page, size, ...filters });
    return fetch(`${API_BASE}/api/admin/transactions?${query}`, { headers: authHeaders() })
      .then(handleResponse<PageResponse<AdminTransactionSummary>>);
  },

  aiCostSummary: (filters: AdminAiCostFilters = {}) => {
    const query = adminQuery({ ...filters });
    return fetch(`${API_BASE}/api/admin/ai-cost/summary?${query}`, { headers: authHeaders() })
      .then(handleResponse<AdminAiCostSummary>);
  },

  aiCostDaily: (filters: AdminAiCostFilters = {}) => {
    const query = adminQuery({ ...filters });
    return fetch(`${API_BASE}/api/admin/ai-cost/daily?${query}`, { headers: authHeaders() })
      .then(handleResponse<AdminAiCostDaily[]>);
  },

  aiCostEvents: (page = 0, size = 10, filters: AdminAiCostEventFilters = {}) => {
    const query = adminQuery({ page, size, ...filters });
    return fetch(`${API_BASE}/api/admin/ai-cost/events?${query}`, { headers: authHeaders() })
      .then(handleResponse<PageResponse<AdminAiUsageEvent>>);
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
  code: "PLAN_BASIC" | "PLAN_STANDARD" | "PLAN_SAVING" | string;
  name: string;
  description: string;
  amount: number;
  planCredits: number;
  editCredits: number;
  suggestionCredits: number;
  highlighted?: boolean;
}

export interface BillingWallet {
  planCredits: number;
  editCredits: number;
  suggestionCredits: number;
}

export interface BillingOrder {
  orderCode: string;
  packageCode: string;
  amount: number;
  planCredits: number;
  editCredits: number;
  suggestionCredits: number;
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
}

export interface AdminTransactionFilters {
  q?: string;
  status?: "ALL" | "PENDING" | "PAID" | "UNDERPAID" | "EXPIRED" | "CANCELLED";
}

export type AdminTransactionStatus = "PENDING" | "PAID" | "UNDERPAID" | "EXPIRED" | "CANCELLED";
export type AdminAiOperation = "PLAN_GENERATION" | "DAY_REGENERATION" | "DESTINATION_SUGGESTION";
export type AdminAiStatus = "SUCCESS" | "INVALID_RESPONSE" | "HTTP_ERROR" | "PARSE_ERROR" | "FAILED";

export interface AdminAiCostFilters {
  from?: string;
  to?: string;
  operation?: "ALL" | AdminAiOperation;
  status?: "ALL" | AdminAiStatus;
}

export interface AdminAiCostEventFilters extends AdminAiCostFilters {
  q?: string;
}

export interface AdminAiOperationAverage {
  operation: AdminAiOperation | string;
  label: string;
  operations: number;
  avgCostVnd: number;
}

export interface AdminAiOperationHealth {
  operation: AdminAiOperation | string;
  label: string;
  requests: number;
  attempts: number;
  retryRate: number;
  errorRate: number;
  avgDurationMs: number;
  maxDurationMs: number;
  totalCostVnd: number;
}

export interface AdminAiCostSummary {
  totalCostVnd: number;
  promptTokens: number;
  outputTokens: number;
  thinkingTokens: number;
  totalTokens: number;
  requests: number;
  attempts: number;
  retryRate: number;
  errorRate: number;
  avgDurationMs: number;
  averageCosts: AdminAiOperationAverage[];
  operationHealth: AdminAiOperationHealth[];
}

export interface AdminAiCostDaily {
  date: string;
  totalCostVnd: number;
}

export interface AdminAiUsageEvent {
  id: number;
  requestId: string;
  attemptNumber: number;
  operation: AdminAiOperation | string;
  status: AdminAiStatus | string;
  userId?: number;
  userEmail?: string;
  tripId?: number;
  model?: string;
  finishReason?: string;
  durationMs?: number;
  promptTokens: number;
  outputTokens: number;
  thinkingTokens: number;
  totalTokens: number;
  maxOutputTokens?: number;
  thinkingBudget?: number;
  estimatedCostVnd: number;
  estimatedCostUsd: number;
  errorCode?: string;
  errorMessage?: string;
  /** Full, untruncated error detail for failed attempts. */
  errorDetail?: string;
  /** Full raw JSON the AI returned before being rejected. */
  rawResponseSnippet?: string;
  createdAt?: string;
}

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
  createdAt?: string;
}

export interface AdminTripDetail {
  trip: TripResponse;
  user: AdminUserSummary;
}

export interface AdminActivityCoordinateResolutionResponse {
  tripId: number;
  dryRun: boolean;
  resolvedCount: number;
  appliedCount: number;
  items: AdminActivityCoordinateResolutionItem[];
}

export interface AdminActivityCoordinateResolutionItem {
  dayNumber?: number;
  activityId?: number;
  sortOrder?: number;
  name?: string;
  type?: string;
  location?: string;
  status: string;
  query?: string;
  displayName?: string;
  latitude?: number;
  longitude?: number;
  confidenceScore?: number;
  coordinateConfidence?: string;
  coordinateSource?: string;
  cacheHit?: boolean;
  applied?: boolean;
  message?: string;
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
  suggestionCredits: number;
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
  overallFit: string;
  overallNote: string;
  budgetFit: string;
  budgetNote: string;
  durationFit: string;
  durationNote: string;
  travelFit: string;
  travelNote: string;
  styleFit: string;
  styleNote: string;
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
  placeId?: number;
  googlePlaceId?: string;
  coordinateSource?: string;
  coordinateConfidence?: string;
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
  coordinateSource?: string;
  coordinateConfidence?: string;
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
  changes: RegenerateActivityChange[];
  unchangedActivityCount: number;
  unchangedActivities: RegenerateUnchangedActivity[];
  metadataUpgradeCount: number;
}

export interface RegenerateActivityChange {
  changeId: string;
  type: "MODIFIED" | "ADDED" | "REMOVED";
  oldActivity?: ActivityResponse;
  newActivity?: ActivityResponse;
  changedFields: Array<"TIME" | "NAME" | "TYPE" | "LOCATION" | "DURATION" | "COST" | "NOTE" | string>;
  oldIndex?: number;
  newIndex?: number;
}

export interface RegenerateUnchangedActivity {
  activity: ActivityResponse;
  metadataUpgradeAvailable: boolean;
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
