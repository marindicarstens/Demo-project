# User Guide — Demo Booking

A walkthrough of what a customer sees and does in the app, screen by screen. Everything described
as "email" below is **simulated** — no real message is ever sent; it's rendered directly in the
app and clearly labelled as a simulation.

---

## 1. Landing page

The only choice on the landing page is **who you are**, because it changes both what information
you're asked for and which appointment types you can book:

- **"I already bank here" (Existing Client)** — for people who already have an account.
- **"I'd like to open an account" (New Account)** — for people who don't bank here yet.

## 2. New Account flow

1. **Basic details** — full name, email, phone. Nothing else is asked, since there's no existing
   record to look up.
2. **Find a branch** — start typing a branch name or city into the search box; a dropdown filters
   as you type. Select one.
3. **Pick a service and time** — choose what you're coming in for (from an onboarding-focused
   list: opening an account, a new-client consultation, general enquiry) and an available date and
   time slot at that branch.
4. Continue to [§4, Confirming your appointment](#4-confirming-your-appointment) below.

## 3. Existing Client flow

1. **Find a branch** and **pick a service and time**, same as above — but from the **full**
   service list (card services, loan consultation, disputes, account maintenance, general enquiry,
   and more), since you're already a client. You're not asked for any personal details yet.
2. **Confirm who you are** — enter your **email, ID number, and account number**. This is checked
   against our records:
   - **Recognised** — your name is pulled from your record automatically (you won't be asked to
     type it), and your booking proceeds to confirmation.
   - **Not recognised** — you'll see a plain message that we couldn't find a matching record, along
     with an option to **continue as a New Account instead** — your branch and time selection carry
     over, so you don't have to pick them again. (You won't be told *which* of the three details
     didn't match — that's deliberate, to keep the check from being usable to guess at other
     people's details.)
3. Continue to [§4, Confirming your appointment](#4-confirming-your-appointment) below.

> **Try it yourself:** a small set of demo "existing client" records are seeded for testing — see
> [README.md § Demo data](../README.md#demo-data) for the exact details to try.

## 4. Confirming your appointment

Once your details are in, your slot is **held for 1 minute** while you confirm — it isn't a firm
booking yet. (Kept short deliberately for this demo, so you don't have to wait around to see what
expiry looks like — see [README.md § Configuration](../README.md#configuration) if you want to
change it.) A simulated confirmation email opens straight away in a **new browser tab**, styled
like an email client and clearly marked **"SIMULATED — no message was actually sent"**. It
contains:

- Your appointment details (branch, service, date, time)
- A **"Confirm my appointment"** link

While the slot is held, the booking page shows your reference code and a **"Confirm by HH:mm"**
deadline, the time the hold runs out. If you closed the email tab, or your browser blocked it,
**"Reopen the email"** opens it again.

Click the link in the email, and the appointment is confirmed. You'll land on a confirmation page
with your reference code. Click **"View receipt email"** there to open your simulated **booking
receipt**, which has a **cancellation link** you can use any time later (see
[§6](#6-cancelling-an-appointment)).

If you refresh the confirmation page or open the link again, it tells you the link has expired or
was already used and offers **"Look up your booking"** to see its status, since each confirmation
link works only once.

**If you don't click the link within 1 minute**, the hold is released — the slot becomes
available to someone else. The booking page doesn't change on its own; when you next click
**"Reopen the email"**, it checks the hold, tells you it expired, and asks you to pick a time
again. Clicking the (now-stale) confirm link gives the same neutral message as above and
points you to **"Look up your booking"**, which shows the appointment as expired, so you always
get a clear outcome either way, not a silent failure. (The backend also generates a simulated
"your appointment could not be confirmed" email behind the scenes at that point — the same expiry
event the confirm page's message reflects — but nothing in the app currently displays that
email's content the way the confirmation and booking-receipt emails are shown; it isn't wired to a
screen yet.)

## 5. Managing an existing booking from the website

Use the **"Look up my booking"** button in the top navigation, and enter your **reference code**
(from your confirmation) and the **email** you booked with. From there you can:

- **Reschedule** to a different available time — allowed up until a minimum-notice cutoff before
  your appointment (e.g. not within the last couple of hours). Picking a new time and clicking
  "Confirm new time" doesn't move your appointment immediately: like the original booking, it
  sends a simulated confirmation email with a link, and your appointment only moves once you click
  it (see §4's confirmation flow above) — your original time is held exactly as it was until then.
  While that request is open, the page shows **"A reschedule is waiting for confirmation"** with a
  **"Reopen the email"** button.
- **Cancel** — allowed at any time right up until your appointment, no minimum-notice restriction.

Straight after you confirm, the confirmation screen can manage the booking for a limited time
without the reference code and email. Once that access has expired, the page says your session
has expired and links to **"Look up your appointment"**, where you can continue with your reference
code and email.

## 6. Cancelling an appointment

You can cancel two ways, and both work identically:

- **From the website**, via "Look up my booking" as above, or directly from the confirmation screen
  right after you confirm (it stays "unlocked" for that browsing session). Clicking **"Cancel
  appointment"** first asks **"Are you sure? This can't be undone."**, and nothing is cancelled
  until you click **"Yes, cancel"**.
- **From your booking-receipt email** — open it with **"View receipt email"** on the confirmation
  screen, then click its **"Cancel my appointment"** link. You'll land on a page showing
  your appointment details and a **"Yes, cancel my appointment"** button. Nothing is cancelled
  until you click that button — opening the link alone never cancels anything, so it's always safe
  to click.

Once cancelled, the slot is immediately released for other customers, and using the same
cancellation link again just confirms it's already cancelled — it won't cause an error.

## 7. What's simulated vs. real

| Behaves exactly like a real system | Simulated for this demo |
|---|---|
| Slot availability, capacity, concurrency safety | No real email or SMS is ever sent |
| Token security (expiring, single-use links) | "Existing client" records are a small seeded demo set, not a real bank's customer database |
| Business rules (minimum notice, capacity limits) | No real account access, payments, or KYC verification happens anywhere |

A disclaimer to this effect appears in the footer of every page.
