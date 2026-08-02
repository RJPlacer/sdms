import Image from "next/image";
import { getVerificationRecord } from "../../../db/verification";

export const dynamic = "force-dynamic";

function formatDate(value: string) {
  const parsed = new Date(`${value.replace(" ", "T")}Z`);
  if (Number.isNaN(parsed.getTime())) return value;
  return new Intl.DateTimeFormat("en-PH", {
    dateStyle: "medium",
    timeZone: "Asia/Manila",
  }).format(parsed);
}

export default async function VerifyPage({ params }: { params: Promise<{ token: string }> }) {
  const { token } = await params;
  const validToken = /^[A-Za-z0-9_-]{32,128}$/.test(token);
  const record = validToken ? await getVerificationRecord(token) : null;

  if (!record) {
    return (
      <main className="verify-shell">
        <section className="verify-card">
          <header className="verify-header">
            <div className="mini-mark">AT</div>
            <div><h1>Athlete Track</h1><p>Official ID verification</p></div>
          </header>
          <div className="not-found">
            <h2>Record not found</h2>
            <p>This QR code is invalid, expired, or has not been published for online verification.</p>
          </div>
        </section>
      </main>
    );
  }

  const active = record.status === "ACTIVE";
  return (
    <main className="verify-shell">
      <section className="verify-card">
        <header className="verify-header">
          <div className="mini-mark">AT</div>
          <div><h1>Athlete Track</h1><p>Palarong Pambansa ID verification</p></div>
        </header>
        <div className={`status-banner ${active ? "" : "revoked"}`}>
          <span className="status-dot" aria-hidden="true" />
          {active ? "Verified active record" : "Record revoked or inactive"}
        </div>
        <div className="profile">
          {active && record.photo_key ? (
            <Image
              className="profile-photo"
              src={`/photo/${token}`}
              alt={`ID photo of ${record.full_name}`}
              width={112}
              height={132}
              unoptimized
            />
          ) : (
            <div className="photo-placeholder">No public photo</div>
          )}
          <div>
            <h2>{record.full_name}</h2>
            <p className="record-type">{record.person_type}</p>
            <dl className="details">
              <div><dt>Registration ID</dt><dd>{record.person_id}</dd></div>
              <div><dt>School / Office</dt><dd>{record.school || "Not specified"}</dd></div>
              {record.descriptor_value && (
                <div><dt>{record.descriptor_label || "Role"}</dt><dd>{record.descriptor_value}</dd></div>
              )}
              <div><dt>Last updated</dt><dd>{formatDate(record.updated_at)}</dd></div>
            </dl>
          </div>
        </div>
        <div className="verification-footer">
          This page confirms that the QR token matches an Athlete Track record. It does not display private contact, birth, guardian, or residential information.
        </div>
      </section>
    </main>
  );
}
