-- Create all PEAD databases
-- Note: pead_earnings_db is the default DB created via POSTGRES_DB env var
CREATE DATABASE pead_market_db;
CREATE DATABASE pead_strategy_db;
CREATE DATABASE pead_trading_db;
CREATE DATABASE pead_backtesting_db;
CREATE DATABASE pead_broker_db;

-- Grant privileges to pead user
GRANT ALL PRIVILEGES ON DATABASE pead_earnings_db TO pead;
GRANT ALL PRIVILEGES ON DATABASE pead_market_db TO pead;
GRANT ALL PRIVILEGES ON DATABASE pead_strategy_db TO pead;
GRANT ALL PRIVILEGES ON DATABASE pead_trading_db TO pead;
GRANT ALL PRIVILEGES ON DATABASE pead_backtesting_db TO pead;
GRANT ALL PRIVILEGES ON DATABASE pead_broker_db TO pead;
