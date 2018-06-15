# aka-sweep

### warning:
 - use at your own risk.  
 - App will automatically load keys and signs transactions.
 - Loss of funds can easily occur if app is run anywhere but a fully secure and trusted environment. 
 - You are responsible for securing your keys
 - The developer is not responsible in any way for how you may use the software, including loss of funds.
 - The developer recommends that you do not use this software.


Configuration (to use aka-sweep, you need to define these):
 - keyfiles: `/secure/UTC--foobar-mn-1.json`, `/secure/UTC--foobar-mn-2.json` 
 - properties file: `/secure/application.properties` with clear text passwords to unlock the account keyfiles 
 - modified `destination.account` address
 - modified `web3.rpc.url` location of local aka rpc node.  (optionally can use public node)

Optional config: 
 - number of blocks to wait for chain confirmation. `C`
 - gas prices
 
 
sweep logic:
 - Load all accounts for keyfiles in given directory
 - wait `C` number of confirmation blocks.
 - For each account, if it holds more than 5,000 aka
   - do nothing if from-address matches txn in mem pool
   - optionally, do nothing if to-address matches txn in mem pool
   - transfer amount greater than 5k to destination address.

chain validation, watching for re-orgs:
 - when a new block is received, a check is made that the parent block hash matches.
 - If a block is received without a matching parent, the chain is walked back until the matching parent is found.
 - The number of blocks that must be walked back is the height of a chain re-org.
 - montioring the chain for large re-orgs is a way to monitor chain health.   
   
run it:
 - in IDE from Application.java
 - `docker run -d -v /mnt/secure/aka-mn-keys:/secure <imageid>`
 - docker-compose build akroma-sweep
 
 
Waiting `C` blocks at start allows time to load transacation mempool, which prevents flooding network with duplicate transactions w same nonce. 
 
 ### Todo:
  - accept cleartext private keys in addition to UTC--keyfile.json/password combo.
  - gracful handing of node reboot.  after reconnect, app will receive flood of old blocks as node re-syncs chain.
  - transactions from all blocks are put in mem pool, even orphan blocks.  no attempt is done to undo adding txn pool when block is found to be orphan. 
  - More chain attack checks:
    - done: check parent block hash match, looking for high re-org height.
    - check same miner mined unreasonably large number of sequential blocks (easily defeated by attacking miner using many addresses)
