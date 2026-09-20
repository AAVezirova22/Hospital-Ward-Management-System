import { ImageResponse } from "next/og";

export const alt =
  "Medcore hospital operations workspace with ward planning and care history";
export const size = { width: 1200, height: 630 };
export const contentType = "image/png";

const stats = [
  ["14", "ACTIVE"],
  ["06", "TRANSFERS"],
  ["02", "ALERTS"],
];

export default function Image() {
  return new ImageResponse(
    (
      <div
        style={{
          display: "flex",
          position: "relative",
          width: "100%",
          height: "100%",
          padding: "58px 64px",
          color: "#f2f6ef",
          background:
            "linear-gradient(120deg, #0d1b17 0%, #132a21 56%, #1d3a2a 100%)",
        }}
      >
        <div
          style={{
            display: "flex",
            position: "absolute",
            top: 0,
            right: 0,
            width: 430,
            height: 430,
            borderRadius: 999,
            background:
              "radial-gradient(circle, rgba(159, 221, 149, 0.22) 0%, rgba(159, 221, 149, 0) 68%)",
          }}
        />

        <div
          style={{
            display: "flex",
            flexDirection: "column",
            justifyContent: "space-between",
            width: 610,
            height: "100%",
          }}
        >
          <div style={{ display: "flex", alignItems: "center", gap: 18 }}>
            <div
              style={{
                display: "flex",
                alignItems: "center",
                justifyContent: "center",
                width: 48,
                height: 48,
                borderRadius: 14,
                background: "#bce8af",
                color: "#10261b",
                fontSize: 26,
                fontWeight: 800,
              }}
            >
              M
            </div>
            <div
              style={{
                display: "flex",
                flexDirection: "column",
                gap: 3,
                fontSize: 18,
                letterSpacing: 3,
                fontWeight: 700,
              }}
            >
              <span>MEDCORE</span>
              <span style={{ color: "#9fb8a8", fontSize: 12, letterSpacing: 2 }}>
                HOSPITAL OPERATIONS
              </span>
            </div>
          </div>

          <div
            style={{
              display: "flex",
              flexDirection: "column",
              gap: 18,
              marginTop: 20,
            }}
          >
            <div
              style={{
                display: "flex",
                color: "#bce8af",
                fontSize: 17,
                fontWeight: 700,
                letterSpacing: 2,
              }}
            >
              SPACE TO FOCUS. ROOM TO CARE.
            </div>
            <div
              style={{
                display: "flex",
                flexDirection: "column",
                fontSize: 62,
                lineHeight: 1.05,
                letterSpacing: -2.5,
                fontWeight: 750,
              }}
            >
              <span>Clearer wards.</span>
              <span>Calmer decisions.</span>
            </div>
            <div
              style={{
                display: "flex",
                maxWidth: 500,
                color: "#b4c9ba",
                fontSize: 20,
                lineHeight: 1.4,
              }}
            >
              Ward planning, care history, and human-confirmed assistance in one
              focused workspace.
            </div>
          </div>

          <div
            style={{
              display: "flex",
              color: "#8da99a",
              fontSize: 13,
              letterSpacing: 1.6,
              fontWeight: 700,
            }}
          >
            WARD PLANNING&nbsp;&nbsp;•&nbsp;&nbsp;CARE HISTORY&nbsp;&nbsp;•&nbsp;&nbsp;ASSISTED WORKFLOWS
          </div>
        </div>

        <div
          style={{
            display: "flex",
            flexDirection: "column",
            position: "absolute",
            right: 62,
            top: 90,
            width: 390,
            height: 450,
            padding: 26,
            border: "1px solid rgba(188, 232, 175, 0.28)",
            borderRadius: 24,
            background: "rgba(9, 25, 19, 0.58)",
            boxShadow: "0 24px 70px rgba(0, 0, 0, 0.22)",
          }}
        >
          <div
            style={{
              display: "flex",
              justifyContent: "space-between",
              alignItems: "center",
              color: "#a9c4b1",
              fontSize: 12,
              letterSpacing: 1.5,
              fontWeight: 700,
            }}
          >
            <span>LIVE WARD PULSE</span>
            <span style={{ color: "#bce8af" }}>07:42 UTC</span>
          </div>
          <div
            style={{
              display: "flex",
              alignItems: "flex-end",
              gap: 10,
              marginTop: 34,
            }}
          >
            <span style={{ fontSize: 68, lineHeight: 0.9, fontWeight: 750 }}>
              24
            </span>
            <span style={{ color: "#a9c4b1", fontSize: 18, paddingBottom: 4 }}>
              / 32 beds in use
            </span>
          </div>
          <div
            style={{
              display: "flex",
              height: 10,
              marginTop: 22,
              borderRadius: 99,
              background: "#294738",
            }}
          >
            <div
              style={{
                display: "flex",
                width: "75%",
                borderRadius: 99,
                background: "#bce8af",
              }}
            />
          </div>
          <div
            style={{
              display: "flex",
              gap: 10,
              marginTop: "auto",
            }}
          >
            {stats.map(([value, label]) => (
              <div
                key={label}
                style={{
                  display: "flex",
                  flexDirection: "column",
                  flex: 1,
                  gap: 8,
                  padding: "14px 12px",
                  borderRadius: 14,
                  border: "1px solid rgba(188, 232, 175, 0.16)",
                  background: "rgba(188, 232, 175, 0.06)",
                }}
              >
                <span style={{ color: "#bce8af", fontSize: 28, fontWeight: 750 }}>
                  {value}
                </span>
                <span style={{ color: "#8da99a", fontSize: 10, letterSpacing: 1 }}>
                  {label}
                </span>
              </div>
            ))}
          </div>
        </div>
      </div>
    ),
    size,
  );
}
