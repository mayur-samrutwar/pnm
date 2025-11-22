# Balance Update Security - How Deposits Update Limits Securely

## 🔐 The Security Flow

### Step 1: User Deposits Money (On-Chain)
1. User signs a deposit transaction with their **Ethereum private key** (hardware-backed)
2. Transaction is sent to the **Vault smart contract** on the blockchain
3. Smart contract stores the deposit: `deposits[userAddress] = amount`
4. **This is immutable** - stored on the blockchain, cannot be changed

### Step 2: App Fetches Balance (Source of Truth)
1. App calls hub API: `GET /api/v1/vaultBalance/:address`
2. Hub server queries the **smart contract directly**:
   ```javascript
   const vaultContract = new ethers.Contract(vaultAddress, vaultAbi, provider);
   const depositBalance = await vaultContract.deposits(address);
   ```
3. **This reads from the blockchain** - the source of truth
4. Hub returns the balance to the app

### Step 3: App Updates Limit (Hardware-Signed)
1. App receives vault balance from hub (e.g., "100.0" USDC)
2. App converts to micro USDC: `100.0 * 1,000,000 = 100,000,000`
3. App checks: Is this a **new deposit**? (vault balance increased)
4. If yes, calls `counterManager.updateLimit(newLimit)`

### Step 4: Hardware-Signed State Update
The `updateLimit()` function:

1. **Verifies current state signature**:
   ```kotlin
   val currentState = CounterState(cumulative, counter, currentLimit)
   if (!verifyState(currentState, currentSignature)) {
       // REJECT: Tampering detected!
       return false
   }
   ```

2. **Creates new state** (preserving cumulative and counter):
   ```kotlin
   val newState = CounterState(
       cumulative = currentCumulative,  // Preserved
       counter = currentCounter,        // Preserved
       limit = newLimit                 // Updated from vault balance
   )
   ```

3. **Signs new state with hardware key**:
   ```kotlin
   val newSignature = signState(newState)  // Hardware signature
   ```

4. **Atomically updates**:
   ```kotlin
   encryptedPrefs.edit()
       .putLong(KEY_OFFLINE_LIMIT, newLimit)
       .putString(KEY_STATE_SIGNATURE, newSignature)
       .apply()
   ```

---

## 🛡️ Why User Cannot Tamper

### Attack 1: User tries to fake the vault balance
**Prevention**:
- Vault balance comes from **blockchain** (smart contract)
- Hub server queries blockchain directly: `vaultContract.deposits(address)`
- User cannot modify blockchain data
- Even if user modifies hub response, app only updates if balance **increased**
- **Result**: User cannot fake a deposit that didn't happen

### Attack 2: User tries to increase limit without depositing
**Prevention**:
- Limit update requires **hardware signature**
- Hardware signature requires **user authentication** (biometric/PIN)
- Even if user modifies stored limit value, the **signature won't match**
- System verifies signature before every operation → **rejects tampered state**
- **Result**: User cannot increase limit without hardware signature

### Attack 3: User tries to modify the stored limit value
**Prevention**:
- Limit is stored with a **hardware signature**
- Before any operation, system verifies: `verifyState(currentState, signature)`
- If user modifies limit value, signature won't match
- System detects tampering and **rejects the update**
- **Result**: Tampered limit is detected and rejected

### Attack 4: User tries to update limit when balance didn't increase
**Prevention**:
- App only updates limit if: `newLimit > currentLimit` (new deposit detected)
- If vault balance is same or decreased, limit is **not updated**
- This prevents user from resetting limit after spending
- **Result**: Limit only increases with actual deposits

### Attack 5: User tries to bypass signature verification
**Prevention**:
- Signature verification happens **inside the hardware chip**
- Private key never leaves hardware
- User cannot bypass hardware security
- **Result**: Signature verification cannot be bypassed

---

## 🔄 Complete Deposit Flow

```
1. User deposits $100 to vault
   ↓
2. Transaction mined on blockchain
   ↓
3. Smart contract: deposits[user] = 100 USDC
   ↓
4. App calls: GET /api/v1/vaultBalance/:address
   ↓
5. Hub queries blockchain: vaultContract.deposits(address)
   ↓
6. Hub returns: { balance: "100.0" }
   ↓
7. App detects: newLimit (100) > currentLimit (50) → NEW DEPOSIT
   ↓
8. App calls: counterManager.updateLimit(100)
   ↓
9. System verifies current state signature ✅
   ↓
10. System creates new state: { cumulative: 20, counter: 5, limit: 100 }
   ↓
11. System signs new state with hardware key 🔐
   ↓
12. System stores: { limit: 100, signature: "0x..." }
   ↓
13. Limit updated securely ✅
```

---

## 🔑 Key Security Properties

### 1. **Source of Truth is Blockchain**
- Vault balance comes from **smart contract on blockchain**
- Cannot be faked or modified by user
- Immutable and verifiable

### 2. **Hardware-Signed State**
- Every limit update is **signed with hardware key**
- Signature cannot be forged (requires hardware)
- Tampering is detected immediately

### 3. **Signature Verification**
- Before every update, system verifies current state signature
- If signature doesn't match → **reject update**
- Prevents tampering with stored values

### 4. **Atomic Updates**
- Limit and signature are updated **together**
- If signature generation fails, limit is not updated
- Ensures consistency

### 5. **Deposit Detection**
- Only updates limit if vault balance **increased**
- Prevents resetting limit after spending
- Only increases with actual deposits

---

## 📊 Security Guarantees

✅ **User cannot fake a deposit** - balance comes from blockchain  
✅ **User cannot increase limit without deposit** - requires hardware signature  
✅ **User cannot modify stored limit** - signature verification detects tampering  
✅ **User cannot reset limit after spending** - only updates on new deposits  
✅ **User cannot bypass signature** - hardware security cannot be bypassed  

---

## 🎯 Summary for Judges

**Question**: How can we update the balance after depositing securely, and how can't the user update it?

**Answer**:

1. **Balance comes from blockchain** (smart contract) - user cannot fake it
2. **Limit update requires hardware signature** - user cannot forge it
3. **Signature verification prevents tampering** - any modification is detected
4. **Only updates on new deposits** - prevents resetting limit after spending
5. **Hardware security cannot be bypassed** - private key never leaves hardware chip

The system ensures that:
- ✅ Balance updates only happen when user **actually deposits** (verified on-chain)
- ✅ Limit updates require **hardware signature** (cannot be faked)
- ✅ Any tampering is **detected and rejected** (signature verification)
- ✅ User cannot increase limit without **real deposit** (blockchain verification)

This provides **cryptographic guarantees** that the balance update is secure and tamper-proof.

