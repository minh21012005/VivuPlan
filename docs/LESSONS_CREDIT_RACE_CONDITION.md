# Lesson: Race Condition Khi Consume Plan Credit

Tài liệu này ghi lại bài học từ bug một user có ít plan credit nhưng vẫn tạo
được nhiều lịch trình hơn số lượt hợp lệ khi gửi nhiều request tạo trip gần như
song song.

## Bối Cảnh

VivuPlan dùng credit để giới hạn các thao tác tốn tài nguyên:

- `PLAN`: tạo lịch trình mới bằng AI.
- `EDIT`: tạo preview tái tạo một ngày.
- `SUGGESTION`: gợi ý điểm đến.

Business rule của tạo trip:

1. User phải còn plan credit trước khi gọi AI.
2. AI có thể tốn thời gian và chi phí.
3. Plan credit chỉ bị trừ sau khi trip được lưu thành công.
4. Nếu không đủ credit ở điểm commit cuối cùng, không được lưu trip và không
   được ghi ledger consume.

Bug production đã gặp:

- User mới chỉ có 1 plan credit nhưng tạo được 2 trip.
- Cả 2 trip đều có ledger `PLAN_GENERATION = -1`.
- Wallet và ledger bị lệch audit: wallet không âm nhưng tổng ledger đã âm.

## Root Cause

Root cause là race condition kiểu check-then-act:

```text
Request A: đọc wallet thấy còn 1 credit
Request B: đọc wallet thấy còn 1 credit
Request A: gọi AI
Request B: gọi AI
Request A: save trip + trừ credit
Request B: save trip + trừ credit
```

Nếu việc check credit và trừ credit không được bảo vệ bởi cùng một thao tác
atomic hoặc lock đúng cách, hai request song song có thể cùng tin rằng user còn
credit.

Điểm dễ nhầm:

- `@Transactional` dài quanh toàn bộ `generateAndSave()` không phải giải pháp
  tốt, vì nó giữ transaction mở trong lúc gọi AI/external services.
- Đọc wallet ở đầu luồng chỉ là pre-check để tránh gọi AI khi user chắc chắn
  hết lượt. Nó không đủ làm điều kiện quyết định cuối cùng.
- Ledger là audit log, không nên là nguồn trừ credit trực tiếp trong flow nóng.
  Wallet là trạng thái hiện tại, ledger là lịch sử để đối soát.

## Giải Pháp Đang Chọn: Atomic Decrement

Giải pháp hiện tại dùng một câu update atomic ở DB:

```sql
update user_wallets
set plan_credits = plan_credits - 1,
    updated_at = current_timestamp
where user_id = :userId
  and plan_credits > 0
```

Trong Spring Data JPA:

```java
@Modifying(flushAutomatically = true)
@Query("""
        update UserWallet w
        set w.planCredits = w.planCredits - 1,
            w.updatedAt = CURRENT_TIMESTAMP
        where w.user.id = :userId
          and w.planCredits > 0
        """)
int decrementPlanCreditIfAvailable(@Param("userId") Long userId);
```

Return value là số row bị update:

- `1`: consume thành công.
- `0`: không có wallet hoặc credit đã hết.

Flow tạo trip:

```text
1. Pre-check requirePlanCredit(userId)
2. Validate input
3. Gọi AI, weather, geocode, enrich place ngoài transaction dài
4. Mở transaction ngắn bằng TransactionTemplate
5. saveAndFlush(trip)
6. consumePlanCredit(userId, trip)
   - atomic decrement
   - nếu update != 1 thì throw BillingException
7. Ghi credit ledger
8. Commit transaction
```

Nếu `consumePlanCredit()` lỗi sau `saveAndFlush()`:

- `saveAndFlush()` đã gửi SQL xuống DB nhưng chưa commit.
- Exception thoát khỏi `TransactionTemplate`.
- Spring rollback toàn bộ transaction.
- Trip, days, activities, wallet update và ledger đều rollback hoặc không được
  ghi.

Quan trọng: `flush != commit`.

## Vì Sao Dùng TransactionTemplate

`TransactionTemplate.execute(...)` tạo một transaction ngắn chỉ bao quanh phần
persistence cuối:

```java
transactionTemplate.execute(status -> persistGeneratedTripAndConsumeCredit(...));
```

Nó tương đương:

```text
begin transaction
try {
  save trip
  consume credit
  write ledger
  commit
} catch (RuntimeException e) {
  rollback
  throw e
}
```

Lý do không để `@Transactional` quanh toàn bộ `generateAndSave()`:

- AI call có thể kéo dài hàng chục giây.
- Giữ DB transaction mở trong lúc gọi AI làm tăng lock time, connection time và
  nguy cơ side effect khó đoán.
- External call không rollback được bằng DB transaction.

Lý do dùng `TransactionTemplate` thay vì private method `@Transactional`:

- Spring transaction proxy không áp dụng cho self-invocation private method.
- `TransactionTemplate` làm transaction boundary rõ ngay trong method
  orchestration.
- Dễ nhìn thấy phần nào chạy ngoài transaction và phần nào commit atomically.

## So Sánh Với PESSIMISTIC_WRITE

Một hướng khác là lock wallet row:

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("select w from UserWallet w where w.user.id = :userId")
Optional<UserWallet> lockByUserId(@Param("userId") Long userId);
```

Flow đúng với pessimistic lock:

```text
begin transaction
select wallet for update
if planCredits <= 0 -> throw
save trip
wallet.planCredits -= 1
write ledger
commit
```

Ưu điểm:

- Dễ hiểu về mặt nghiệp vụ.
- Có thể mutate nhiều field wallet trong cùng một object.
- Phù hợp khi cần nhiều quyết định phức tạp dựa trên wallet state.

Nhược điểm:

- Phụ thuộc transaction boundary rất chặt.
- Nếu `open-in-view=true` hoặc transaction/session boundary không rõ, dễ tạo
  cảm giác lock đã bảo vệ nhưng thực tế behavior không như mong muốn.
- Lock row lâu hơn atomic update nếu trong transaction có nhiều logic.

Bài học từ nhánh thử nghiệm:

- Khi bật `spring.jpa.open-in-view=false`, pessimistic lock hoạt động đúng hơn
  vì persistence context không bị kéo dài qua web request.
- Tuy nhiên với use case trừ đúng 1 credit nếu còn credit, atomic decrement vẫn
  gọn hơn và ít lock choreography hơn.

## Vì Sao Không Ưu Tiên Optimistic Lock Cho Case Này

Optimistic lock thường dùng `@Version`:

```text
Request A đọc wallet version 1
Request B đọc wallet version 1
Request A update -> version 2
Request B update version 1 -> OptimisticLockException
```

Ưu điểm:

- Tốt cho conflict hiếm.
- Không giữ DB lock lâu trong lúc đọc.

Nhược điểm với credit consumption:

- Cần thêm version column/migration.
- Phải xử lý retry hoặc convert exception thành lỗi nghiệp vụ.
- Với credit, thao tác mong muốn rất đơn giản: "trừ nếu còn > 0". DB atomic
  update diễn đạt rule này trực tiếp hơn.
- Nếu retry không cẩn thận, có thể vô tình tạo thêm side effect.

Kết luận: optimistic lock không sai về mặt kỹ thuật, nhưng không phải lựa chọn
tối ưu nhất cho hot path consume credit hiện tại.

## Vì Sao Không Dùng Reservation/Refund Ở Bước Này

Một phương án mạnh hơn:

```text
1. Reserve/trừ credit trước khi gọi AI
2. Nếu AI/save fail thì refund lại
3. Nếu success thì finalize ledger
```

Ưu điểm:

- Chặn cả việc gọi AI dư khi user chỉ còn 1 credit.
- Kiểm soát chi phí AI tốt hơn trong race.

Nhược điểm:

- Phức tạp hơn: cần trạng thái reservation, timeout, refund, idempotency.
- Phải xử lý crash giữa reserve và refund.
- Ledger phải phân biệt reserved/final/refunded hoặc có bảng reservation riêng.

Với quyết định hiện tại, hệ thống chấp nhận trade-off:

- Race hiếm có thể gọi AI dư.
- Nhưng chỉ một request được save trip và consume credit.
- Giải pháp đơn giản, ít thay đổi public API và ít rủi ro hơn reservation.

## Open In View Và Bài Học Lazy Loading

`spring.jpa.open-in-view=true` giữ persistence context mở tới tận lúc render
response. Điều này tiện nhưng dễ che giấu bug:

- Controller có thể vô tình truy cập lazy association ngoài service.
- Transaction boundary nhìn trong code không còn rõ.
- Các lỗi lazy loading chỉ lộ ra khi tắt OSIV hoặc lên production với flow khác.

Khi chuyển sang:

```yaml
spring:
  jpa:
    open-in-view: false
```

mọi lazy association cần được load/map trong service transaction.

Bug đã lộ:

- `getUserTrips()` map `Trip.itineraryDays` sau khi session đã đóng.

Fix:

```java
@Transactional(readOnly = true)
public List<TripDto.TripResponse> getUserTrips(Long userId) {
    ...
}
```

Một điểm khác đã harden:

- Admin trip detail không trả `User` entity từ service về controller nữa.
- Service map luôn thành `AdminDto.UserSummary` trong transaction.

Nguyên tắc rút ra:

- Controller không nên nhận entity JPA để tự map DTO.
- Service nên trả DTO hoặc đảm bảo entity đã được fetch đầy đủ.
- OSIV false giúp transaction boundary trung thực hơn.

## Ledger Audit Và Data Cleanup

Wallet là state hiện tại:

```text
user_wallets.plan_credits
```

Ledger là lịch sử:

```text
credit_ledger(type, delta, reason, trip_id, payment_order_id)
```

Query đối soát:

```sql
select
  w.user_id,
  w.plan_credits as wallet_plan,
  coalesce(sum(l.delta) filter (where l.type = 'PLAN'), 0) as ledger_plan
from user_wallets w
left join credit_ledger l on l.user_id = w.user_id
group by w.user_id, w.plan_credits
having w.plan_credits <> coalesce(sum(l.delta) filter (where l.type = 'PLAN'), 0)
order by w.user_id;
```

Nếu có lệch do bug cũ, nên sửa bằng ledger adjustment thay vì xóa lịch sử:

```sql
insert into credit_ledger (user_id, type, delta, reason, trip_id, created_at)
values (:userId, 'PLAN', :delta, 'ADMIN_ADJUSTMENT', null, now());
```

Không nên xóa ledger cũ vì mất dấu audit.

## Checklist Khi Sửa Các Flow Credit

Trước khi merge:

- Có pre-check trước external call tốn tiền không?
- Có final atomic check/consume ở điểm thành công không?
- Save business entity và consume credit có cùng transaction không?
- Nếu consume fail thì entity vừa save có rollback không?
- Ledger có được ghi cùng transaction với wallet mutation không?
- Có test race hoặc ít nhất test final consume fail rollback không?
- Có query đối soát wallet vs ledger không?
- Controller có trả entity JPA ra ngoài service không?
- OSIV false có làm lộ lazy-loading bug không?

## Test Cases Nên Có

Backend service tests:

- AI fail -> không save trip, không consume credit.
- Save fail -> không consume credit, không ghi ledger.
- Atomic decrement trả `0` -> throw insufficient credit, rollback trip.
- Atomic decrement trả `1` -> ghi ledger và trả response.
- Hai consume liên tiếp khi chỉ còn 1 credit -> một thành công, một fail.
- Wallet và ledger khớp sau các flow payment/generate.

Integration hoặc manual production-like tests:

- User còn 1 credit, mở 2 tab tạo trip cùng lúc:
  - Có thể cả 2 gọi AI.
  - Chỉ 1 trip được persist.
  - Wallet còn 0.
  - Ledger có đúng 1 dòng `PLAN_GENERATION`.
- User còn 2 credit, mở 2 tab tạo trip cùng lúc:
  - Cả 2 trip được persist.
  - Wallet giảm 2.
  - Ledger có 2 dòng `PLAN_GENERATION`.

## Kết Luận

Bug này không chỉ là "thiếu lock". Nó là bài học về việc đặt đúng ranh giới
giữa:

- pre-check để tránh tốn tài nguyên;
- final check để đảm bảo business invariant;
- transaction ngắn để commit state;
- atomic DB operation để chống race;
- audit ledger để đối soát;
- OSIV false để buộc service layer sở hữu DTO mapping.

Với VivuPlan hiện tại, atomic decrement là lựa chọn hợp lý nhất cho consume
credit vì rule đơn giản, hiệu quả, ít thay đổi kiến trúc, và bảo vệ được invariant
quan trọng nhất: **không persist trip nếu không consume được plan credit**.
