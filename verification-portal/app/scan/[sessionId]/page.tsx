import PhoneScanner from "./phone-scanner";

export default async function ScannerPage({ params }: { params: Promise<{ sessionId: string }> }) {
  const { sessionId } = await params;
  return <PhoneScanner sessionId={sessionId} />;
}
