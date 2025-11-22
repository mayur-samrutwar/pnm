import * as fs from 'fs';
import * as path from 'path';

interface DepositRecord {
  user: string;
  amount: string;
  token: string;
  txHash: string;
  timestamp: number;
}

interface SlipRecord {
  slipId: string;
  payer?: string;
  userAddress?: string;
  amount: string;
  status: string;
  timestamp: number;
}

interface RefillRequest {
  userAddress: string;
  nonce: string;
  newLimit: number;
  expiry: number;
  token: string;
  timestamp: number;
  proof?: string;
}

interface Database {
  usedSlips: Set<string>;
  deposits: DepositRecord[];
  slips: SlipRecord[];
  refillRequests?: RefillRequest[];
}

const DB_FILE_PATH = path.join(__dirname, '../../data/db.json');

export class InMemoryDB {
  private db: Database;
  private initialized: boolean = false;

  constructor() {
    this.db = {
      usedSlips: new Set<string>(),
      deposits: [],
      slips: [],
      refillRequests: [],
    };
  }

  /**
   * Initialize the database by loading from JSON file if it exists
   */
  async initialize(): Promise<void> {
    if (this.initialized) return;

    try {
      // Ensure data directory exists
      const dataDir = path.dirname(DB_FILE_PATH);
      if (!fs.existsSync(dataDir)) {
        fs.mkdirSync(dataDir, { recursive: true });
      }

      // Load existing data if file exists
      if (fs.existsSync(DB_FILE_PATH)) {
        const fileContent = fs.readFileSync(DB_FILE_PATH, 'utf-8');
        const data = JSON.parse(fileContent);
        
        this.db.usedSlips = new Set(data.usedSlips || []);
        this.db.deposits = data.deposits || [];
        this.db.slips = data.slips || [];
        this.db.refillRequests = data.refillRequests || [];
      }
    } catch (error) {
      console.error('Error initializing database:', error);
      // Start with empty database if file is corrupted
    }

    this.initialized = true;
  }

  /**
   * Check if a slip ID has been used
   */
  isSlipUsed(slipId: string): boolean {
    return this.db.usedSlips.has(slipId);
  }

  /**
   * Mark a slip ID as used
   */
  async markSlipUsed(slipId: string): Promise<void> {
    this.db.usedSlips.add(slipId);
    await this.persist();
  }

  /**
   * Record a deposit
   */
  async recordDeposit(user: string, amount: string, token: string, txHash: string): Promise<void> {
    this.db.deposits.push({
      user,
      amount,
      token,
      txHash,
      timestamp: Date.now(),
    });
    await this.persist();
  }

  /**
   * Get all deposits for a user
   */
  getDeposits(user: string): DepositRecord[] {
    return this.db.deposits.filter((d) => d.user.toLowerCase() === user.toLowerCase());
  }

  /**
   * Get all deposits
   */
  getAllDeposits(): DepositRecord[] {
    return [...this.db.deposits];
  }

  /**
   * Persist database to JSON file
   */
  private async persist(): Promise<void> {
    try {
      const data = {
        usedSlips: Array.from(this.db.usedSlips),
        deposits: this.db.deposits,
      };
      fs.writeFileSync(DB_FILE_PATH, JSON.stringify(data, null, 2), 'utf-8');
    } catch (error) {
      console.error('Error persisting database:', error);
      throw error;
    }
  }

  /**
   * Export database state to a specific file (useful for debugging)
   */
  async exportToFile(filePath: string): Promise<void> {
    try {
      const data = {
        usedSlips: Array.from(this.db.usedSlips),
        deposits: this.db.deposits,
      };
      fs.writeFileSync(filePath, JSON.stringify(data, null, 2), 'utf-8');
    } catch (error) {
      console.error('Error exporting database:', error);
      throw error;
    }
  }

  /**
   * Reset database (useful for testing)
   */
  reset(): void {
    this.db = {
      usedSlips: new Set<string>(),
      deposits: [],
      slips: [],
      refillRequests: [],
    };
  }

  /**
   * Clear all used slips (useful for testing)
   */
  async clearUsedSlips(): Promise<void> {
    this.db.usedSlips.clear();
    await this.persist();
  }

  /**
   * Remove a specific slip from used slips (useful for retrying failed redemptions)
   */
  async removeUsedSlip(slipId: string): Promise<void> {
    this.db.usedSlips.delete(slipId);
    await this.persist();
  }

  /**
   * Get database state as object (for inspection)
   */
  getState(): { usedSlips: string[]; deposits: DepositRecord[]; slips: SlipRecord[]; refillRequests: RefillRequest[] } {
    return {
      usedSlips: Array.from(this.db.usedSlips),
      deposits: [...this.db.deposits],
      slips: [...this.db.slips],
      refillRequests: [...(this.db.refillRequests || [])],
    };
  }

  /**
   * Get slips array (for refill verification)
   */
  get slips(): SlipRecord[] {
    return [...this.db.slips];
  }

  /**
   * Add slip record
   */
  async addSlip(slip: SlipRecord): Promise<void> {
    this.db.slips.push(slip);
    await this.persist();
  }

  /**
   * Update slip status
   */
  async updateSlipStatus(slipId: string, status: string): Promise<void> {
    const slip = this.db.slips.find(s => s.slipId === slipId);
    if (slip) {
      slip.status = status;
      await this.persist();
    }
  }
}

// Singleton instance
let dbInstance: InMemoryDB | null = null;

export function getDB(): InMemoryDB {
  if (!dbInstance) {
    dbInstance = new InMemoryDB();
  }
  return dbInstance;
}

