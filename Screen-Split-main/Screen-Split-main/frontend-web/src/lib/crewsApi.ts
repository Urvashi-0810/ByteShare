import { pb } from "@/lib/pocketbase";
import type { FriendEdge } from "@/lib/friendsApi";
import { friendPayloadToUser } from "@/lib/friendsApi";
import type { Group } from "@/lib/mockStore";
import { store } from "@/lib/mockStore";

export type CrewDTO = {
  id: string;
  name: string;
  emoji: string;
  inviteCode: string;
  bill: number;
  memberIds: string[];
  members?: FriendEdge["user"][];
};

export function crewDtoToGroup(d: CrewDTO): Group {
  return {
    id: d.id,
    name: d.name,
    emoji: d.emoji,
    inviteCode: d.inviteCode,
    bill: d.bill,
    memberIds: d.memberIds,
    vouchedHostage: null,
    challenges: [],
    sabbaths: {},
  };
}

/** PocketBase-backed crews use opaque ids; local demo crews use `g_*`. */
export function isPersistedCrewId(id: string): boolean {
  return !id.startsWith("g_") && id !== "social-group";
}

export async function fetchMyCrewDtos(): Promise<CrewDTO[]> {
  const res = await pb.send<{ items: CrewDTO[] }>("/crews/mine", {
    method: "GET",
  });
  return res.items ?? [];
}

export async function refreshMyCrewsInStore(meId: string): Promise<void> {
  const items = await fetchMyCrewDtos();
  const fromCrews = items.flatMap((c) => c.members ?? []).map(friendPayloadToUser);
  if (fromCrews.length) {
    store.mergeRemoteUsers(fromCrews);
  }
  store.mergeMyRemoteCrews(items.map(crewDtoToGroup), meId);
}

export async function createCrewOnServer(
  meId: string,
  body: { name: string; emoji: string; bill: number },
): Promise<CrewDTO> {
  const dto = await pb.send<CrewDTO>("/crews", {
    method: "POST",
    body,
  });
  await refreshMyCrewsInStore(meId);
  return dto;
}

export async function joinCrewByCode(
  meId: string,
  code: string,
): Promise<{ status: "joined" | "already_member"; crew: CrewDTO }> {
  const res = await pb.send<{
    status: "joined" | "already_member";
    crew: CrewDTO;
  }>("/crews/join", {
    method: "POST",
    body: { code: code.trim() },
  });
  await refreshMyCrewsInStore(meId);
  return res;
}
