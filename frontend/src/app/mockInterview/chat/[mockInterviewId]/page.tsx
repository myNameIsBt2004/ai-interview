import { redirect } from "next/navigation";

export default function LegacyChatPage({
  params,
}: {
  params: { mockInterviewId: string };
}) {
  redirect(`/interview/room/${params.mockInterviewId}`);
}
