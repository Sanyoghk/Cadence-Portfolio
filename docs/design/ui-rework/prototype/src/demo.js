// Synthetic fixtures only. No account, source bytes, API or token storage.
export const DEMO_DATE = "2026-10-19";
export const PILLARS = [
  {
    name: "The making",
    weight: 40,
    description:
      "Let people see the hands, materials and decisions behind every piece.",
    formats: "Process film · Studio notes",
  },
  {
    name: "Everyday rituals",
    weight: 35,
    description:
      "Give your work a place in the small moments people want to keep.",
    formats: "Carousel · Short story",
  },
  {
    name: "People & purpose",
    weight: 25,
    description: "Share the people and principles that make the studio yours.",
    formats: "Founder note · Conversation",
  },
];
export const METRICS = [
  {
    date: "12 Oct",
    title: "Slow mornings",
    platform: "Instagram",
    impressions: 1200,
    engagements: 84,
  },
  {
    date: "14 Oct",
    title: "Meet your maker",
    platform: "TikTok",
    impressions: 1600,
    engagements: 96,
  },
  {
    date: "16 Oct",
    title: "A cup worth keeping",
    platform: "Instagram",
    impressions: 900,
    engagements: 63,
  },
  {
    date: "18 Oct",
    title: "From clay to table",
    platform: "TikTok",
    impressions: 1300,
    engagements: 104,
  },
];
export function metricSummary(rows) {
  const impressions = rows.reduce((sum, r) => sum + r.impressions, 0);
  const engagements = rows.reduce((sum, r) => sum + r.engagements, 0);
  return {
    impressions,
    engagements,
    rate: impressions ? (engagements / impressions) * 100 : null,
  };
}
export function initialBrand(name = "Sunday Clay") {
  return {
    name,
    sourceVersion: 1,
    consent: null,
    consentHistory: [],
    withdrawn: false,
    identity: null,
    identityHistory: [],
    strategy: null,
    strategyHistory: [],
    posts: [
      {
        id: "cup",
        title: "A slower kind of morning",
        caption:
          "A cup to hold a moment. Made by hand, ready for your everyday ritual.",
        platform: "Instagram",
        date: "2026-10-19",
        time: "10:00",
        status: "APPROVED",
        version: 1,
      },
      {
        id: "hands",
        title: "The hands behind the piece",
        caption:
          "From a lump of clay to something you will reach for every day.",
        platform: "TikTok",
        date: "2026-10-21",
        time: "12:00",
        status: "DRAFT",
        version: 1,
      },
      {
        id: "table",
        title: "Make room for the everyday",
        caption: "Small objects. Good company. A table that feels like yours.",
        platform: "Instagram",
        date: "2026-10-23",
        time: "17:00",
        status: "APPROVED",
        version: 1,
      },
      {
        id: "notes",
        title: "Notes from the studio",
        caption:
          "A week of quiet progress, shared from our studio to your home.",
        platform: "TikTok",
        date: "2026-10-25",
        time: "10:00",
        status: "DRAFT",
        version: 1,
      },
    ],
    postHistory: [],
  };
}
export function createDemoWorkspaces() {
  return [
    {
      id: "studio",
      name: "Studio Sunday",
      industry: "Independent ceramics",
      brands: [{ id: "clay", ...initialBrand() }],
    },
    {
      id: "field",
      name: "Field Notes",
      industry: "Independent publishing",
      brands: [
        { id: "notes", ...initialBrand("Field Notes Press"), posts: [] },
      ],
    },
  ];
}
export function selectBrand(workspaces, workspaceId, brandId) {
  return (
    workspaces
      .find((w) => w.id === workspaceId && !w.archived)
      ?.brands.find((b) => b.id === brandId) ?? null
  );
}
export function currentConsent(brand) {
  return (
    !!brand.consent &&
    brand.consent.version === brand.sourceVersion &&
    !brand.withdrawn
  );
}
export function currentIdentity(brand) {
  return (
    currentConsent(brand) &&
    brand.identity?.status === "APPROVED" &&
    brand.identity.sourceVersion === brand.sourceVersion
  );
}
export function admit(brand, version) {
  if (version !== brand.sourceVersion)
    throw new Error("The source changed. Review it again.");
  return {
    ...brand,
    consent: { version },
    consentHistory: [...brand.consentHistory, { version, action: "ALLOWED" }],
    withdrawn: false,
  };
}
export function withdrawConsent(brand) {
  if (!currentConsent(brand))
    throw new Error("No current demo consent to withdraw.");
  return {
    ...brand,
    withdrawn: true,
    consentHistory: [
      ...brand.consentHistory,
      { version: brand.sourceVersion, action: "WITHDRAWN" },
    ],
  };
}
export function prepareStrategy(brand, goal) {
  if (!currentIdentity(brand))
    throw new Error(
      "Approve an Identity from the current reviewed source first.",
    );
  if (!goal.trim() || goal.length > 500)
    throw new Error("Enter a goal of 1–500 characters.");
  return {
    ...brand,
    strategy: {
      version: (brand.strategy?.version ?? 0) + 1,
      status: "DRAFT",
      goal: goal.trim(),
      sourceVersion: brand.identity.version,
    },
  };
}
export function approveStrategy(brand) {
  if (
    !currentIdentity(brand) ||
    brand.strategy?.status !== "DRAFT" ||
    brand.strategy.sourceVersion !== brand.identity.version
  )
    throw new Error("A current Strategy draft is required.");
  const strategy = { ...brand.strategy, status: "APPROVED" };
  return {
    ...brand,
    strategy,
    strategyHistory: [...brand.strategyHistory, strategy],
  };
}
export function currentStrategy(brand) {
  return (
    currentIdentity(brand) &&
    brand.strategy?.status === "APPROVED" &&
    brand.strategy.sourceVersion === brand.identity.version
  );
}
export function prepareContent(brand) {
  if (!currentStrategy(brand))
    throw new Error(
      "Approve a current Strategy before preparing demo content.",
    );
  if (brand.posts.length >= 12)
    throw new Error("This demo is limited to twelve samples per Brand.");
  const number = brand.posts.length + 1;
  return {
    ...brand,
    posts: [
      ...brand.posts,
      {
        id: "sample-" + number,
        title: "A moment in the making " + number,
        caption:
          "From our studio to your everyday ritual. A synthetic sample to review and make your own.",
        platform: "Instagram",
        date: "2026-10-22",
        time: "11:00",
        status: "DRAFT",
        version: 1,
      },
    ],
  };
}
export function metricsFor(brand) {
  return brand.id === "clay" ? METRICS : [];
}
export function generateIdentity(brand) {
  if (!currentConsent(brand))
    throw new Error("Review and allow this exact demo source first.");
  return {
    ...brand,
    identity: {
      version: (brand.identity?.version ?? 0) + 1,
      status: "DRAFT",
      voice: "Warm, considered and quietly confident.",
      sourceVersion: brand.sourceVersion,
    },
  };
}
export function approveIdentity(brand) {
  if (
    !currentConsent(brand) ||
    brand.identity?.status !== "DRAFT" ||
    brand.identity.sourceVersion !== brand.sourceVersion
  )
    throw new Error("A current reviewed draft is required.");
  const identity = { ...brand.identity, status: "APPROVED" };
  return {
    ...brand,
    identity,
    identityHistory: [...brand.identityHistory, identity],
  };
}
export function editIdentity(brand, voice) {
  if (!brand.identity || !voice.trim() || voice.length > 500)
    throw new Error("Enter a voice note of 1–500 characters.");
  return {
    ...brand,
    identity: {
      ...brand.identity,
      voice: voice.trim(),
      version: brand.identity.version + 1,
      status: "DRAFT",
    },
  };
}
export function approvePost(brand, id, caption) {
  const post = brand.posts.find((p) => p.id === id);
  if (post?.status !== "DRAFT" || !caption.trim() || caption.length > 2000)
    throw new Error("Choose a draft and enter a caption of 1–2000 characters.");
  const approved = {
    ...post,
    caption: caption.trim(),
    version: post.version + 1,
    status: "APPROVED",
  };
  return {
    ...brand,
    posts: brand.posts.map((p) => (p.id === id ? approved : p)),
    postHistory: [...brand.postHistory, { ...post }, approved],
  };
}
export function csvFor(brand) {
  const cell = (v) =>
    '"' +
    (/^[=+\-@\t\r]/.test(String(v)) ? "'" + v : String(v)).replaceAll(
      '"',
      '""',
    ) +
    '"';
  return (
    "\uFEFF" +
    [
      ["Demo data", "Platform", "Date", "Time (Europe/London)", "Caption"],
      ...brand.posts
        .filter((p) => p.status === "APPROVED")
        .map((p) => ["SYNTHETIC", p.platform, p.date, p.time, p.caption]),
    ]
      .map((row) => row.map(cell).join(","))
      .join("\r\n")
  );
}
export function schedulePost(brand, id, date, time) {
  const post = brand.posts.find((p) => p.id === id);
  if (post?.status !== "APPROVED")
    throw new Error("Approve this revision before scheduling.");
  if (
    !/^2026-10-(19|2[0-5])$/.test(date) ||
    !/^(1[0-8]):[0-5][0-9]$/.test(time)
  )
    throw new Error(
      "Demo scheduling supports 19–25 October, 10:00–18:59 Europe/London.",
    );
  return {
    ...brand,
    posts: brand.posts.map((p) =>
      p.id === id ? { ...p, date, time, version: p.version + 1 } : p,
    ),
  };
}
