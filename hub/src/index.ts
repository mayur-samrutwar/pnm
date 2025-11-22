import express from 'express';
import dotenv from 'dotenv';
import voucherRoutes from './routes/voucher';
import { getDB } from './db/inMemory';

dotenv.config();

const app = express();
const PORT = process.env.PORT || 3000;

app.use(express.json());

// Health check endpoint
app.get('/health', (req, res) => {
  res.json({ status: 'ok', message: 'Hub server is running' });
});

// Voucher API routes
app.use('/api/v1', voucherRoutes);

// Initialize database on startup
async function startServer() {
  const db = getDB();
  await db.initialize();
  console.log('Database initialized');

  app.listen(PORT, () => {
    console.log(`Server is running on port ${PORT}`);
    console.log(`Environment: ${process.env.NODE_ENV || 'development'}`);
    console.log(`Redeem on-chain: ${process.env.REDEEM_ON_CHAIN === 'true' ? 'enabled' : 'disabled'}`);
  });
}

startServer().catch((error) => {
  console.error('Failed to start server:', error);
  process.exit(1);
});

