export default function Home() {
  return (
    <main className="landing-shell">
      <section className="landing-card">
        <div className="brand-mark" aria-hidden="true">AT</div>
        <p className="eyebrow">Palarong Pambansa</p>
        <h1>Athlete Track Verification</h1>
        <p className="lead">
          Scan the QR code printed on an Athlete Track ID card to confirm that
          the record is registered and active.
        </p>
        <div className="privacy-note">
          <strong>Privacy protected</strong>
          <span>Only approved identification details are shown publicly.</span>
        </div>
      </section>
      <footer>Sports Delegation Management System</footer>
    </main>
  );
}
