import { pb } from "@/lib/pocketbase";
import type { User } from "@/lib/mockStore";

export type FriendEdge = {
  friendshipId: string;
  user: {
    id: string;
    name: string;
    email: string;
    avatar: string;
    streak: number;
    badges: string[];
    analytics: { date: string; usageMinutes: number }[];
    bio: string;
    joinDate: string;
  };
};

export type IncomingFriendRequest = {
  id: string;
  fromUser: FriendEdge["user"];
  created: string;
};

export type OutgoingFriendRequest = {
  id: string;
  toUser: FriendEdge["user"];
  created: string;
};

export type RequestByEmailStatus =
  | "created"
  | "already_friends"
  | "request_pending"
  | "now_friends";

export function friendRecordToUser(edge: FriendEdge): User {
  const u = edge.user;
  const analytics = Array.isArray(u.analytics) ? u.analytics : [];
  return {
    id: u.id,
    name: u.name || "Friend",
    email: u.email,
    avatar: u.avatar || "👤",
    streak: u.streak ?? 0,
    badges: Array.isArray(u.badges) ? u.badges : [],
    analytics,
    friendIds: [],
    bio: u.bio?.trim() || "",
    joinDate: u.joinDate || "—",
    friendshipId: edge.friendshipId,
  };
}

export function friendPayloadToUser(u: FriendEdge["user"]): User {
  return friendRecordToUser({ friendshipId: "", user: u });
}

export async function fetchFriends(): Promise<FriendEdge[]> {
  const res = await pb.send<{ items: FriendEdge[] }>("/friends", {
    method: "GET",
  });
  return res.items ?? [];
}

export async function fetchIncomingRequests(): Promise<IncomingFriendRequest[]> {
  const res = await pb.send<{ items: IncomingFriendRequest[] }>(
    "/friends/incoming-requests",
    { method: "GET" },
  );
  return res.items ?? [];
}

export async function fetchOutgoingRequests(): Promise<OutgoingFriendRequest[]> {
  const res = await pb.send<{ items: OutgoingFriendRequest[] }>(
    "/friends/outgoing-requests",
    { method: "GET" },
  );
  return res.items ?? [];
}

export async function requestFriendByEmail(email: string): Promise<{
  status: RequestByEmailStatus;
  requestId?: string;
  friendshipId?: string;
  user: FriendEdge["user"];
}> {
  return pb.send<{
    status: RequestByEmailStatus;
    requestId?: string;
    friendshipId?: string;
    user: FriendEdge["user"];
  }>("/friends/request-by-email", {
    method: "POST",
    body: { email: email.trim() },
  });
}

export async function acceptFriendRequest(requestId: string): Promise<{
  friendshipId: string;
  user: FriendEdge["user"];
}> {
  return pb.send(`/friends/requests/${encodeURIComponent(requestId)}/accept`, {
    method: "POST",
  });
}

export async function rejectFriendRequest(requestId: string): Promise<void> {
  await pb.send(`/friends/requests/${encodeURIComponent(requestId)}/reject`, {
    method: "POST",
  });
}

export async function cancelFriendRequest(requestId: string): Promise<void> {
  await pb.send(`/friends/requests/${encodeURIComponent(requestId)}/cancel`, {
    method: "POST",
  });
}

export async function removeFriend(friendshipId: string): Promise<void> {
  await pb.send(`/friends/${encodeURIComponent(friendshipId)}/remove`, {
    method: "POST",
  });
}
