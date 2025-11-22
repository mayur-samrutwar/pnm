import express from 'express';
import dotenv from 'dotenv';
import voucherRoutes from './routes/voucher';
import refillRoutes from './routes/refill';
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
// Refill API routes
app.use('/api/v1', refillRoutes);

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

// Export app for testing
export { app };

// Only start server if this file is run directly (not imported)
if (require.main === module) {
  startServer().catch((error) => {
    console.error('Failed to start server:', error);
    process.exit(1);
  });
}

