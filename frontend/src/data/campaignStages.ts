export interface CampaignStage {
  id: number;
  displayName: string;
  description: string;
  position: { x: number; y: number };
  status: 'active' | 'locked' | 'completed';
}

export const MAP_WIDTH = 7168;
export const MAP_HEIGHT = 3584;

export const CAMPAIGN_STAGES: CampaignStage[] = [
  {
    id: 1,
    displayName: 'Lone bandit apprentice',
    description: 'A fresh recruit barely knows which end of the bow to hold.',
    position: { x: 6200, y: 2900 },
    status: 'active',
  },
  {
    id: 2,
    displayName: 'Forest scouts',
    description: "Two riders patrol the king's road.",
    position: { x: 5700, y: 2450 },
    status: 'active',
  },
  {
    id: 3,
    displayName: "Sheriff's tax collector",
    description: 'A greedy collector returning with coin from the villages.',
    position: { x: 4800, y: 2300 },
    status: 'active',
  },
  {
    id: 4,
    displayName: 'Roving mercenaries',
    description: 'Hired blades from across the channel.',
    position: { x: 4250, y: 1900 },
    status: 'locked',
  },
  {
    id: 5,
    displayName: "Sir Guy's lieutenant",
    description: 'The right hand of Sir Guy of Gisbourne himself.',
    position: { x: 3600, y: 1600 },
    status: 'locked',
  },
  {
    id: 6,
    displayName: "Black Knight's squire",
    description: 'A young knight seeking to prove his worth.',
    position: { x: 3000, y: 1800 },
    status: 'locked',
  },
  {
    id: 7,
    displayName: 'Captain of the Guard',
    description: "Commander of Nottingham's town watch.",
    position: { x: 2150, y: 1900 },
    status: 'locked',
  },
  {
    id: 8,
    displayName: 'Sir Guy of Gisbourne',
    description: "The sheriff's favored champion.",
    position: { x: 1550, y: 1800 },
    status: 'locked',
  },
  {
    id: 9,
    displayName: "Prince John's envoy",
    description: 'Sent from the prince himself to crush the rebellion.',
    position: { x: 1100, y: 1550 },
    status: 'locked',
  },
  {
    id: 10,
    displayName: 'Sheriff of Nottingham',
    description: 'The final confrontation deep in the forest.',
    position: { x: 950, y: 1100 },
    status: 'locked',
  },
];
