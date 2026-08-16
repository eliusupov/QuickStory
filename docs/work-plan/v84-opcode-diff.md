# v83 -> v84 opcode diff (ticket 21)

Generated from the Chronicle20/atlas `gms_v84` registry, cross-checked against the live v84
routing table `template_gms_84_1.json` and the v84 IDB export. Cosmic keys are joined to atlas
rows by **v83 opcode position**, not by name.

| direction | keys | corrected | confirmed/unchanged | unresolved | cosmic-internal |
|---|---|---|---|---|---|
| send (clientbound) | 307 | 11 | 295 | 1 | 0 |
| recv (serverbound) | 178 | 7 | 165 | 4 | 2 |

## Registry adjudication

Rows in `gms_v84.yaml` above opcode `0x3E` whose v84 value still equalled the v83 value -
the delta=0 islands in an otherwise monotonically rising shift curve. `provenance` does *not*
identify these (task-100 reshifted 188 rows without updating it), the curve does.

| | clientbound | serverbound | total |
|---|---|---|---|
| stale rows found | 11 | 46 | 57 |
| corrected | 11 | 9 | 20 |
| confirmed unchanged | 0 | 18 | 18 |
| UNRESOLVED | 0 | 19 | 19 |

## sendops (clientbound) - 307 keys

| key | v83 | v84 | delta | status | evidence |
|---|---|---|---|---|---|
| `LOGIN_STATUS` | `0x00` | `0x00` | +0 | ok | atlas LOGIN_STATUS (csv-import); inside the 0x00-0x3E band ticket 20 proved unchanged |
| `GUEST_ID_LOGIN` | `0x01` | `0x01` | +0 | ok | atlas GUEST_ID_LOGIN (csv-import); inside the 0x00-0x3E band ticket 20 proved unchanged |
| `ACCOUNT_INFO` | `0x02` | `0x02` | +0 | ok | atlas ACCOUNT_INFO (csv-import); inside the 0x00-0x3E band ticket 20 proved unchanged |
| `SERVERSTATUS` | `0x03` | `0x03` | +0 | ok | atlas SERVERSTATUS (csv-import); inside the 0x00-0x3E band ticket 20 proved unchanged |
| `GENDER_DONE` | `0x04` | `0x04` | +0 | ok | atlas GENDER_DONE (csv-import); inside the 0x00-0x3E band ticket 20 proved unchanged |
| `CONFIRM_EULA_RESULT` | `0x05` | `0x05` | +0 | ok | atlas CONFIRM_EULA_RESULT (csv-import); inside the 0x00-0x3E band ticket 20 proved unchanged |
| `CHECK_PINCODE` | `0x06` | `0x06` | +0 | ok | atlas CHECK_PINCODE (csv-import); inside the 0x00-0x3E band ticket 20 proved unchanged |
| `UPDATE_PINCODE` | `0x07` | `0x07` | +0 | ok | atlas UPDATE_PINCODE (csv-import); inside the 0x00-0x3E band ticket 20 proved unchanged |
| `VIEW_ALL_CHAR` | `0x08` | `0x08` | +0 | ok | atlas VIEW_ALL_CHAR (csv-import); inside the 0x00-0x3E band ticket 20 proved unchanged |
| `SELECT_CHARACTER_BY_VAC` | `0x09` | `0x09` | +0 | ok | atlas SELECT_CHARACTER_BY_VAC (csv-import); inside the 0x00-0x3E band ticket 20 proved unchanged |
| `SERVERLIST` | `0x0A` | `0x0A` | +0 | ok | atlas WORLD_INFORMATION (csv-import); inside the 0x00-0x3E band ticket 20 proved unchanged |
| `CHARLIST` | `0x0B` | `0x0B` | +0 | ok | atlas CHARLIST (csv-import); inside the 0x00-0x3E band ticket 20 proved unchanged |
| `SERVER_IP` | `0x0C` | `0x0C` | +0 | ok | atlas SERVER_IP (csv-import); inside the 0x00-0x3E band ticket 20 proved unchanged |
| `CHAR_NAME_RESPONSE` | `0x0D` | `0x0D` | +0 | ok | atlas CHAR_NAME_RESPONSE (csv-import); inside the 0x00-0x3E band ticket 20 proved unchanged |
| `ADD_NEW_CHAR_ENTRY` | `0x0E` | `0x0E` | +0 | ok | atlas ADD_NEW_CHAR_ENTRY (csv-import); inside the 0x00-0x3E band ticket 20 proved unchanged |
| `DELETE_CHAR_RESPONSE` | `0x0F` | `0x0F` | +0 | ok | atlas DELETE_CHAR_RESPONSE (csv-import); inside the 0x00-0x3E band ticket 20 proved unchanged |
| `CHANGE_CHANNEL` | `0x10` | `0x10` | +0 | ok | atlas CHANGE_CHANNEL (csv-import); inside the 0x00-0x3E band ticket 20 proved unchanged |
| `PING` | `0x11` | `0x11` | +0 | ok | atlas PING (csv-import); inside the 0x00-0x3E band ticket 20 proved unchanged |
| `KOREAN_INTERNET_CAFE_SHIT` | `0x12` | `0x12` | +0 | ok | atlas KOREAN_INTERNET_CAFE_SHIT (csv-import); inside the 0x00-0x3E band ticket 20 proved unchanged |
| `CHANNEL_SELECTED` | `0x14` | `0x14` | +0 | ok | atlas CHANNEL_SELECTED (csv-import); inside the 0x00-0x3E band ticket 20 proved unchanged |
| `HACKSHIELD_REQUEST` | `0x15` | `0x15` | +0 | ok | atlas HACKSHIELD_REQUEST (csv-import); inside the 0x00-0x3E band ticket 20 proved unchanged |
| `RELOG_RESPONSE` | `0x16` | `0x16` | +0 | ok | atlas RELOG_RESPONSE (manual); inside the 0x00-0x3E band ticket 20 proved unchanged |
| `CHECK_CRC_RESULT` | `0x19` | `0x19` | +0 | ok | atlas CHECK_CRC_RESULT (csv-import); inside the 0x00-0x3E band ticket 20 proved unchanged |
| `LAST_CONNECTED_WORLD` | `0x1A` | `0x1A` | +0 | ok | atlas LAST_CONNECTED_WORLD (csv-import); inside the 0x00-0x3E band ticket 20 proved unchanged |
| `RECOMMENDED_WORLD_MESSAGE` | `0x1B` | `0x1B` | +0 | ok | atlas RECOMMENDED_WORLD_MESSAGE (csv-import); inside the 0x00-0x3E band ticket 20 proved unchanged |
| `CHECK_SPW_RESULT` | `0x1C` | `0x1C` | +0 | ok | atlas CHECK_SPW_RESULT (csv-import); inside the 0x00-0x3E band ticket 20 proved unchanged |
| `INVENTORY_OPERATION` | `0x1D` | `0x1D` | +0 | ok | atlas INVENTORY_OPERATION (csv-import); inside the 0x00-0x3E band ticket 20 proved unchanged |
| `INVENTORY_GROW` | `0x1E` | `0x1E` | +0 | ok | atlas INVENTORY_GROW (csv-import); inside the 0x00-0x3E band ticket 20 proved unchanged |
| `STAT_CHANGED` | `0x1F` | `0x1F` | +0 | ok | atlas STAT_CHANGED (csv-import); inside the 0x00-0x3E band ticket 20 proved unchanged |
| `GIVE_BUFF` | `0x20` | `0x20` | +0 | ok | atlas GIVE_BUFF (csv-import); inside the 0x00-0x3E band ticket 20 proved unchanged |
| `CANCEL_BUFF` | `0x21` | `0x21` | +0 | ok | atlas CANCEL_BUFF (csv-import); inside the 0x00-0x3E band ticket 20 proved unchanged |
| `FORCED_STAT_SET` | `0x22` | `0x22` | +0 | ok | atlas FORCED_STAT_SET (csv-import); inside the 0x00-0x3E band ticket 20 proved unchanged |
| `FORCED_STAT_RESET` | `0x23` | `0x23` | +0 | ok | atlas FORCED_STAT_RESET (csv-import); inside the 0x00-0x3E band ticket 20 proved unchanged |
| `UPDATE_SKILLS` | `0x24` | `0x24` | +0 | ok | atlas UPDATE_SKILLS (csv-import); inside the 0x00-0x3E band ticket 20 proved unchanged |
| `SKILL_USE_RESULT` | `0x25` | `0x25` | +0 | ok | atlas SKILL_USE_RESULT (csv-import); inside the 0x00-0x3E band ticket 20 proved unchanged |
| `FAME_RESPONSE` | `0x26` | `0x26` | +0 | ok | atlas FAME_RESPONSE (csv-import); inside the 0x00-0x3E band ticket 20 proved unchanged |
| `SHOW_STATUS_INFO` | `0x27` | `0x27` | +0 | ok | atlas SHOW_STATUS_INFO (csv-import); inside the 0x00-0x3E band ticket 20 proved unchanged |
| `OPEN_FULL_CLIENT_DOWNLOAD_LINK` | `0x28` | `0x28` | +0 | ok | atlas OPEN_FULL_CLIENT_DOWNLOAD_LINK (csv-import); inside the 0x00-0x3E band ticket 20 proved unchanged |
| `MEMO_RESULT` | `0x29` | `0x29` | +0 | ok | atlas MEMO_RESULT (csv-import); inside the 0x00-0x3E band ticket 20 proved unchanged |
| `MAP_TRANSFER_RESULT` | `0x2A` | `0x2A` | +0 | ok | atlas MAP_TRANSFER_RESULT (csv-import); inside the 0x00-0x3E band ticket 20 proved unchanged |
| `WEDDING_PHOTO` | `0x2B` | `0x2B` | +0 | ok | atlas WEDDING_PHOTO (csv-import); inside the 0x00-0x3E band ticket 20 proved unchanged |
| `CLAIM_RESULT` | `0x2D` | `0x2D` | +0 | ok | atlas CLAIM_RESULT (ida-discovered); inside the 0x00-0x3E band ticket 20 proved unchanged |
| `CLAIM_AVAILABLE_TIME` | `0x2E` | `0x2E` | +0 | ok | atlas CLAIM_AVAILABLE_TIME (ida-discovered); inside the 0x00-0x3E band ticket 20 proved unchanged |
| `CLAIM_STATUS_CHANGED` | `0x2F` | `0x2F` | +0 | ok | atlas CLAIM_STATUS_CHANGED (ida-discovered); inside the 0x00-0x3E band ticket 20 proved unchanged |
| `SET_TAMING_MOB_INFO` | `0x30` | `0x30` | +0 | ok | atlas SET_TAMING_MOB_INFO (csv-import); inside the 0x00-0x3E band ticket 20 proved unchanged |
| `QUEST_CLEAR` | `0x31` | `0x31` | +0 | ok | atlas QUEST_CLEAR (csv-import); inside the 0x00-0x3E band ticket 20 proved unchanged |
| `ENTRUSTED_SHOP_CHECK_RESULT` | `0x32` | `0x32` | +0 | ok | atlas ENTRUSTED_SHOP_CHECK_RESULT (csv-import); inside the 0x00-0x3E band ticket 20 proved unchanged |
| `SKILL_LEARN_ITEM_RESULT` | `0x33` | `0x33` | +0 | ok | atlas SKILL_LEARN_ITEM_RESULT (csv-import); inside the 0x00-0x3E band ticket 20 proved unchanged |
| `GATHER_ITEM_RESULT` | `0x34` | `0x34` | +0 | ok | atlas GATHER_ITEM_RESULT (csv-import); inside the 0x00-0x3E band ticket 20 proved unchanged |
| `SORT_ITEM_RESULT` | `0x35` | `0x35` | +0 | ok | atlas SORT_ITEM_RESULT (csv-import); inside the 0x00-0x3E band ticket 20 proved unchanged |
| `SUE_CHARACTER_RESULT` | `0x37` | `0x37` | +0 | ok | atlas SUE_CHARACTER_RESULT (ida-discovered); inside the 0x00-0x3E band ticket 20 proved unchanged |
| `TRADE_MONEY_LIMIT` | `0x39` | `0x39` | +0 | ok | atlas TRADE_MONEY_LIMIT (csv-import); inside the 0x00-0x3E band ticket 20 proved unchanged |
| `SET_GENDER` | `0x3A` | `0x3A` | +0 | ok | atlas SET_GENDER (csv-import); inside the 0x00-0x3E band ticket 20 proved unchanged |
| `GUILD_BBS_PACKET` | `0x3B` | `0x3B` | +0 | ok | atlas GUILD_BBS_PACKET (csv-import); inside the 0x00-0x3E band ticket 20 proved unchanged |
| `CHAR_INFO` | `0x3D` | `0x3D` | +0 | ok | atlas CHAR_INFO (csv-import); inside the 0x00-0x3E band ticket 20 proved unchanged |
| `PARTY_OPERATION` | `0x3E` | `0x3E` | +0 | ok | atlas PARTY_OPERATION (csv-import); inside the 0x00-0x3E band ticket 20 proved unchanged |
| `BUDDYLIST` | `0x3F` | `0x41` | +2 | ok | atlas BUDDYLIST (manual); +2, re-derived from the v84 IDB dispatch |
| `GUILD_OPERATION` | `0x41` | `0x43` | +2 | ok | atlas GUILD_OPERATION (ida-discovered); +2, re-derived from the v84 IDB dispatch |
| `ALLIANCE_OPERATION` | `0x42` | `0x44` | +2 | corrected | atlas ALLIANCE_OPERATION; shift +2 between IDB anchors; 0x44 vacant |
| `SPAWN_PORTAL` | `0x43` | `0x45` | +2 | ok | atlas SPAWN_PORTAL (ida-discovered); +2, re-derived from the v84 IDB dispatch |
| `SERVERMESSAGE` | `0x44` | `0x46` | +2 | corrected | atlas SERVERMESSAGE; shift +2 between IDB anchors; 0x46 vacant |
| `INCUBATOR_RESULT` | `0x45` | `0x47` | +2 | ok | atlas INCUBATOR_RESULT (ida-discovered); +2, re-derived from the v84 IDB dispatch |
| `SHOP_SCANNER_RESULT` | `0x46` | `0x48` | +2 | ok | atlas SHOP_SCANNER_RESULT (ida-discovered); +2, re-derived from the v84 IDB dispatch |
| `SHOP_LINK_RESULT` | `0x47` | `0x49` | +2 | ok | atlas SHOP_LINK_RESULT (ida-discovered); +2, re-derived from the v84 IDB dispatch |
| `MARRIAGE_REQUEST` | `0x48` | `0x4A` | +2 | ok | atlas MARRIAGE_REQUEST (ida-discovered); +2, re-derived from the v84 IDB dispatch |
| `MARRIAGE_RESULT` | `0x49` | `0x4B` | +2 | ok | atlas MARRIAGE_RESULT (ida-discovered); +2, re-derived from the v84 IDB dispatch |
| `WEDDING_GIFT_RESULT` | `0x4A` | `0x4C` | +2 | ok | atlas WEDDING_GIFT_RESULT (ida-discovered); +2, re-derived from the v84 IDB dispatch |
| `NOTIFY_MARRIED_PARTNER_MAP_TRANSFER` | `0x4B` | `0x4D` | +2 | ok | atlas NOTIFY_MARRIED_PARTNER_MAP_TRANSFER (ida-discovered); +2, re-derived from the v84 IDB dispatch |
| `CASH_PET_FOOD_RESULT` | `0x4C` | `0x4E` | +2 | ok | atlas CASH_PET_FOOD_RESULT (manual); +2, re-derived from the v84 IDB dispatch |
| `SET_WEEK_EVENT_MESSAGE` | `0x4D` | `0x4F` | +2 | ok | atlas SET_WEEK_EVENT_MESSAGE (ida-discovered); +2, re-derived from the v84 IDB dispatch |
| `SET_POTION_DISCOUNT_RATE` | `0x4E` | `0x50` | +2 | ok | atlas SET_POTION_DISCOUNT_RATE (ida-discovered); +2, re-derived from the v84 IDB dispatch |
| `BRIDLE_MOB_CATCH_FAIL` | `0x4F` | `0x51` | +2 | ok | atlas BRIDLE_MOB_CATCH_FAIL (ida-discovered); +2, re-derived from the v84 IDB dispatch |
| `IMITATED_NPC_RESULT` | `0x50` | `0x52` | +2 | ok | atlas IMITATED_NPC_RESULT (ida-discovered); +2, re-derived from the v84 IDB dispatch |
| `IMITATED_NPC_DATA` | `0x51` | `0x53` | +2 | ok | atlas IMITATED_NPC_DATA (ida-discovered); +2, re-derived from the v84 IDB dispatch |
| `LIMITED_NPC_DISABLE_INFO` | `0x52` | `0x54` | +2 | ok | atlas LIMITED_NPC_DISABLE_INFO (ida-discovered); +2, re-derived from the v84 IDB dispatch |
| `MONSTER_BOOK_SET_CARD` | `0x53` | `0x55` | +2 | ok | atlas MONSTER_BOOK_SET_CARD (ida-discovered); +2, re-derived from the v84 IDB dispatch |
| `MONSTER_BOOK_SET_COVER` | `0x54` | `0x56` | +2 | ok | atlas MONSTER_BOOK_SET_COVER (ida-discovered); +2, re-derived from the v84 IDB dispatch |
| `HOUR_CHANGED` | `0x55` | `0x57` | +2 | ok | atlas HOUR_CHANGED (ida-discovered); +2, re-derived from the v84 IDB dispatch |
| `MINIMAP_ON_OFF` | `0x56` | `0x58` | +2 | ok | atlas MINIMAP_ON_OFF (ida-discovered); +2, re-derived from the v84 IDB dispatch |
| `CONSULT_AUTHKEY_UPDATE` | `0x57` | `0x59` | +2 | ok | atlas CONSULT_AUTHKEY_UPDATE (ida-discovered); +2, re-derived from the v84 IDB dispatch |
| `CLASS_COMPETITION_AUTHKEY_UPDATE` | `0x58` | `0x5A` | +2 | ok | atlas CLASS_COMPETITION_AUTHKEY_UPDATE (ida-discovered); +2, re-derived from the v84 IDB dispatch |
| `WEB_BOARD_AUTHKEY_UPDATE` | `0x59` | `0x5B` | +2 | ok | atlas WEB_BOARD_AUTHKEY_UPDATE (ida-discovered); +2, re-derived from the v84 IDB dispatch |
| `SESSION_VALUE` | `0x5A` | `0x5C` | +2 | ok | atlas SESSION_VALUE (ida-discovered); +2, re-derived from the v84 IDB dispatch |
| `PARTY_VALUE` | `0x5B` | `0x5D` | +2 | ok | atlas PARTY_VALUE (ida-discovered); +2, re-derived from the v84 IDB dispatch |
| `FIELD_SET_VARIABLE` | `0x5C` | `0x5E` | +2 | ok | atlas FIELD_SET_VARIABLE (ida-discovered); +2, re-derived from the v84 IDB dispatch |
| `BONUS_EXP_CHANGED` | `0x5D` | `0x5F` | +2 | ok | atlas BONUS_EXP_CHANGED (ida-discovered); +2, re-derived from the v84 IDB dispatch |
| `FAMILY_CHART_RESULT` | `0x5E` | `0x61` | +3 | ok | atlas FAMILY_CHART_RESULT (ida-discovered); +3, re-derived from the v84 IDB dispatch |
| `FAMILY_INFO_RESULT` | `0x5F` | `0x62` | +3 | ok | atlas FAMILY_INFO_RESULT (ida-discovered); +3, re-derived from the v84 IDB dispatch |
| `FAMILY_RESULT` | `0x60` | `0x63` | +3 | ok | atlas FAMILY_RESULT (ida-discovered); +3, re-derived from the v84 IDB dispatch |
| `FAMILY_JOIN_REQUEST` | `0x61` | `0x64` | +3 | ok | atlas FAMILY_JOIN_REQUEST (ida-discovered); +3, re-derived from the v84 IDB dispatch |
| `FAMILY_JOIN_REQUEST_RESULT` | `0x62` | `0x65` | +3 | ok | atlas FAMILY_JOIN_REQUEST_RESULT (ida-discovered); +3, re-derived from the v84 IDB dispatch |
| `FAMILY_JOIN_ACCEPTED` | `0x63` | `0x66` | +3 | ok | atlas FAMILY_JOIN_ACCEPTED (ida-discovered); +3, re-derived from the v84 IDB dispatch |
| `FAMILY_PRIVILEGE_LIST` | `0x64` | `0x67` | +3 | ok | atlas FAMILY_PRIVILEGE_LIST (ida-discovered); +3, re-derived from the v84 IDB dispatch |
| `FAMILY_REP_GAIN` | `0x65` | `0x68` | +3 | ok | atlas FAMILY_REP_GAIN (ida-discovered); +3, re-derived from the v84 IDB dispatch |
| `FAMILY_NOTIFY_LOGIN_OR_LOGOUT` | `0x66` | `0x69` | +3 | ok | atlas FAMILY_NOTIFY_LOGIN_OR_LOGOUT (ida-discovered); +3, re-derived from the v84 IDB dispatch |
| `FAMILY_SET_PRIVILEGE` | `0x67` | `0x6A` | +3 | ok | atlas FAMILY_SET_PRIVILEGE (ida-discovered); +3, re-derived from the v84 IDB dispatch |
| `FAMILY_SUMMON_REQUEST` | `0x68` | `0x6B` | +3 | ok | atlas FAMILY_SUMMON_REQUEST (ida-discovered); +3, re-derived from the v84 IDB dispatch |
| `NOTIFY_LEVELUP` | `0x69` | `0x6C` | +3 | ok | atlas NOTIFY_LEVELUP (ida-discovered); +3, re-derived from the v84 IDB dispatch |
| `NOTIFY_MARRIAGE` | `0x6A` | `0x6D` | +3 | ok | atlas NOTIFY_MARRIAGE (ida-discovered); +3, re-derived from the v84 IDB dispatch |
| `NOTIFY_JOB_CHANGE` | `0x6B` | `0x6E` | +3 | ok | atlas NOTIFY_JOB_CHANGE (ida-discovered); +3, re-derived from the v84 IDB dispatch |
| `MAPLE_TV_USE_RES` | `0x6D` | `0x70` | +3 | ok | atlas MAPLE_TV_USE_RES (ida-discovered); +3, re-derived from the v84 IDB dispatch |
| `AVATAR_MEGAPHONE_RESULT` | `0x6E` | `0x71` | +3 | ok | atlas AVATAR_MEGAPHONE_RESULT (ida-discovered); +3, re-derived from the v84 IDB dispatch |
| `SET_AVATAR_MEGAPHONE` | `0x6F` | `0x72` | +3 | ok | atlas SET_AVATAR_MEGAPHONE (ida-discovered); +3, re-derived from the v84 IDB dispatch |
| `CLEAR_AVATAR_MEGAPHONE` | `0x70` | `0x73` | +3 | ok | atlas CLEAR_AVATAR_MEGAPHONE (ida-discovered); +3, re-derived from the v84 IDB dispatch |
| `CANCEL_NAME_CHANGE_RESULT` | `0x71` | `0x74` | +3 | ok | atlas CANCEL_NAME_CHANGE_RESULT (ida-discovered); +3, re-derived from the v84 IDB dispatch |
| `CANCEL_TRANSFER_WORLD_RESULT` | `0x72` | `0x75` | +3 | ok | atlas CANCEL_TRANSFER_WORLD_RESULT (ida-discovered); +3, re-derived from the v84 IDB dispatch |
| `DESTROY_SHOP_RESULT` | `0x73` | `0x76` | +3 | ok | atlas DESTROY_SHOP_RESULT (ida-discovered); +3, re-derived from the v84 IDB dispatch |
| `FAKE_GM_NOTICE` | `0x74` | `0x77` | +3 | ok | atlas FAKE_GM_NOTICE (ida-discovered); +3, re-derived from the v84 IDB dispatch |
| `SUCCESS_IN_USE_GACHAPON_BOX` | `0x75` | `0x78` | +3 | ok | atlas SUCCESS_IN_USE_GACHAPON_BOX (ida-discovered); +3, re-derived from the v84 IDB dispatch |
| `NEW_YEAR_CARD_RES` | `0x76` | `0x79` | +3 | ok | atlas NEW_YEAR_CARD_RES (ida-discovered); +3, re-derived from the v84 IDB dispatch |
| `RANDOM_MORPH_RES` | `0x77` | `0x7A` | +3 | ok | atlas RANDOM_MORPH_RES (ida-discovered); +3, re-derived from the v84 IDB dispatch |
| `CANCEL_NAME_CHANGE_BY_OTHER` | `0x78` | `0x7B` | +3 | ok | atlas CANCEL_NAME_CHANGE_BY_OTHER (ida-discovered); +3, re-derived from the v84 IDB dispatch |
| `SET_EXTRA_PENDANT_SLOT` | `0x79` | `0x7C` | +3 | ok | atlas SET_EXTRA_PENDANT_SLOT (ida-discovered); +3, re-derived from the v84 IDB dispatch |
| `SCRIPT_PROGRESS_MESSAGE` | `0x7A` | `0x7D` | +3 | ok | atlas SCRIPT_PROGRESS_MESSAGE (manual); +3, re-derived from the v84 IDB dispatch |
| `DATA_CRC_CHECK_FAILED` | `0x7B` | `0x7E` | +3 | ok | atlas DATA_CRC_CHECK_FAILED (ida-discovered); +3, re-derived from the v84 IDB dispatch |
| `MACRO_SYS_DATA_INIT` | `0x7C` | `0x7F` | +3 | ok | atlas MACRO_SYS_DATA_INIT (ida-discovered); +3, re-derived from the v84 IDB dispatch |
| `SET_FIELD` | `0x7D` | `0x80` | +3 | ok | atlas SET_FIELD (ida-discovered); +3, re-derived from the v84 IDB dispatch |
| `SET_ITC` | `0x7E` | `0x81` | +3 | ok | atlas SET_ITC (ida-discovered); +3, re-derived from the v84 IDB dispatch |
| `SET_CASH_SHOP` | `0x7F` | `0x82` | +3 | ok | atlas SET_CASH_SHOP (ida-discovered); +3, re-derived from the v84 IDB dispatch |
| `SET_BACK_EFFECT` | `0x80` | `0x83` | +3 | ok | atlas SET_BACK_EFFECT (ida-discovered); +3, re-derived from the v84 IDB dispatch |
| `SET_MAP_OBJECT_VISIBLE` | `0x81` | `0x84` | +3 | ok | atlas SET_MAP_OBJECT_VISIBLE (ida-discovered); +3, re-derived from the v84 IDB dispatch |
| `CLEAR_BACK_EFFECT` | `0x82` | `0x85` | +3 | ok | atlas CLEAR_BACK_EFFECT (ida-discovered); +3, re-derived from the v84 IDB dispatch |
| `BLOCKED_MAP` | `0x83` | `0x86` | +3 | ok | atlas BLOCKED_MAP (manual); +3, re-derived from the v84 IDB dispatch |
| `BLOCKED_SERVER` | `0x84` | `0x87` | +3 | ok | atlas BLOCKED_SERVER (manual); +3, re-derived from the v84 IDB dispatch |
| `FORCED_MAP_EQUIP` | `0x85` | `0x88` | +3 | ok | atlas FORCED_MAP_EQUIP (manual); +3, re-derived from the v84 IDB dispatch |
| `MULTICHAT` | `0x86` | `0x89` | +3 | ok | atlas MULTICHAT (manual); +3, re-derived from the v84 IDB dispatch |
| `WHISPER` | `0x87` | `0x8A` | +3 | ok | atlas WHISPER (manual); +3, re-derived from the v84 IDB dispatch |
| `SPOUSE_CHAT` | `0x88` | `0x8B` | +3 | ok | atlas SPOUSE_CHAT (ida-discovered); +3, re-derived from the v84 IDB dispatch |
| `SUMMON_ITEM_INAVAILABLE` | `0x89` | `0x8C` | +3 | ok | atlas SUMMON_ITEM_INAVAILABLE (manual); +3, re-derived from the v84 IDB dispatch |
| `FIELD_EFFECT` | `0x8A` | `0x8D` | +3 | ok | atlas FIELD_EFFECT (ida-discovered); +3, re-derived from the v84 IDB dispatch |
| `FIELD_OBSTACLE_ONOFF` | `0x8B` | `0x8E` | +3 | ok | atlas FIELD_OBSTACLE_ONOFF (manual); +3, re-derived from the v84 IDB dispatch |
| `FIELD_OBSTACLE_ONOFF_LIST` | `0x8C` | `0x8F` | +3 | ok | atlas FIELD_OBSTACLE_ONOFF_LIST (manual); +3, re-derived from the v84 IDB dispatch |
| `FIELD_OBSTACLE_ALL_RESET` | `0x8D` | `0x90` | +3 | ok | atlas FIELD_OBSTACLE_ALL_RESET (manual); +3, re-derived from the v84 IDB dispatch |
| `BLOW_WEATHER` | `0x8E` | `0x91` | +3 | ok | atlas BLOW_WEATHER (manual); +3, re-derived from the v84 IDB dispatch |
| `PLAY_JUKEBOX` | `0x8F` | `0x92` | +3 | ok | atlas PLAY_JUKEBOX (manual); +3, re-derived from the v84 IDB dispatch |
| `ADMIN_RESULT` | `0x90` | `0x93` | +3 | ok | atlas ADMIN_RESULT (csv-import); +3, re-derived from the v84 IDB dispatch |
| `OX_QUIZ` | `0x91` | `0x94` | +3 | ok | atlas OX_QUIZ (manual); +3, re-derived from the v84 IDB dispatch |
| `GMEVENT_INSTRUCTIONS` | `0x92` | `0x95` | +3 | ok | atlas GMEVENT_INSTRUCTIONS (manual); +3, re-derived from the v84 IDB dispatch |
| `CLOCK` | `0x93` | `0x96` | +3 | ok | atlas CLOCK (manual); +3, re-derived from the v84 IDB dispatch |
| `CONTI_MOVE` | `0x94` | `0x97` | +3 | ok | atlas CONTI_MOVE (csv-import); +3, re-derived from the v84 IDB dispatch |
| `CONTI_STATE` | `0x95` | `0x98` | +3 | ok | atlas CONTI_STATE (manual); +3, re-derived from the v84 IDB dispatch |
| `SET_QUEST_CLEAR` | `0x96` | `0x99` | +3 | ok | atlas SET_QUEST_CLEAR (manual); +3, re-derived from the v84 IDB dispatch |
| `SET_QUEST_TIME` | `0x97` | `0x9A` | +3 | ok | atlas SET_QUEST_TIME (manual); +3, re-derived from the v84 IDB dispatch |
| `ARIANT_RESULT` | `0x98` | `0x9B` | +3 | ok | atlas ARIANT_RESULT (csv-import); +3, re-derived from the v84 IDB dispatch |
| `SET_OBJECT_STATE` | `0x99` | `0x9C` | +3 | ok | atlas SET_OBJECT_STATE (manual); +3, re-derived from the v84 IDB dispatch |
| `STOP_CLOCK` | `0x9A` | `0x9D` | +3 | ok | atlas STOP_CLOCK (manual); +3, re-derived from the v84 IDB dispatch |
| `ARIANT_ARENA_SHOW_RESULT` | `0x9B` | `0x9E` | +3 | ok | atlas ARIANT_ARENA_SHOW_RESULT (csv-import); +3, re-derived from the v84 IDB dispatch |
| `PYRAMID_GAUGE` | `0x9D` | `0xA0` | +3 | ok | atlas PYRAMID_GAUGE (csv-import); +3, re-derived from the v84 IDB dispatch |
| `PYRAMID_SCORE` | `0x9E` | `0xA1` | +3 | ok | atlas PYRAMID_SCORE (csv-import); +3, re-derived from the v84 IDB dispatch |
| `QUICKSLOT_INIT` | `0x9F` | `0xA2` | +3 | corrected | atlas QUICKSLOT_INIT; shift +3 between IDB anchors; 0xA2 vacant |
| `SPAWN_PLAYER` | `0xA0` | `0xA3` | +3 | ok | atlas SPAWN_PLAYER (manual); +3, re-derived from the v84 IDB dispatch |
| `REMOVE_PLAYER_FROM_MAP` | `0xA1` | `0xA4` | +3 | ok | atlas REMOVE_PLAYER_FROM_MAP (manual); +3, re-derived from the v84 IDB dispatch |
| `CHATTEXT` | `0xA2` | `0xA5` | +3 | ok | atlas CHATTEXT (ida-discovered); +3, re-derived from the v84 IDB dispatch |
| `CHATTEXT1` | `0xA3` | `0xA6` | +3 | ok | atlas CHATTEXT1 (ida-discovered); +3, re-derived from the v84 IDB dispatch |
| `CHALKBOARD` | `0xA4` | `0xA7` | +3 | ok | atlas CHALKBOARD (ida-discovered); +3, re-derived from the v84 IDB dispatch |
| `UPDATE_CHAR_BOX` | `0xA5` | `0xA8` | +3 | ok | atlas UPDATE_CHAR_BOX (ida-discovered); +3, re-derived from the v84 IDB dispatch |
| `SHOW_CONSUME_EFFECT` | `0xA6` | `0xA9` | +3 | ok | atlas SHOW_CONSUME_EFFECT (ida-discovered); +3, re-derived from the v84 IDB dispatch |
| `SHOW_SCROLL_EFFECT` | `0xA7` | `0xAA` | +3 | ok | atlas SHOW_SCROLL_EFFECT (ida-discovered); +3, re-derived from the v84 IDB dispatch |
| `SPAWN_PET` | `0xA8` | `0xAB` | +3 | ok | atlas SPAWN_PET (ida-discovered); +3, re-derived from the v84 IDB dispatch |
| `MOVE_PET` | `0xAA` | `0xAE` | +4 | ok | atlas MOVE_PET (ida-discovered); +4, re-derived from the v84 IDB dispatch |
| `PET_CHAT` | `0xAB` | `0xAF` | +4 | ok | atlas PET_CHAT (ida-discovered); +4, re-derived from the v84 IDB dispatch |
| `PET_NAMECHANGE` | `0xAC` | `0xB0` | +4 | ok | atlas PET_NAMECHANGE (ida-discovered); +4, re-derived from the v84 IDB dispatch |
| `PET_EXCEPTION_LIST` | `0xAD` | `0xB1` | +4 | ok | atlas PET_EXCEPTION_LIST (ida-discovered); +4, re-derived from the v84 IDB dispatch |
| `PET_COMMAND` | `0xAE` | `0xB2` | +4 | ok | atlas PET_COMMAND (ida-discovered); +4, re-derived from the v84 IDB dispatch |
| `SPAWN_SPECIAL_MAPOBJECT` | `0xAF` | `0xB3` | +4 | ok | atlas SPAWN_SPECIAL_MAPOBJECT (manual); +4, re-derived from the v84 IDB dispatch |
| `REMOVE_SPECIAL_MAPOBJECT` | `0xB0` | `0xB4` | +4 | ok | atlas REMOVE_SPECIAL_MAPOBJECT (manual); +4, re-derived from the v84 IDB dispatch |
| `MOVE_SUMMON` | `0xB1` | `0xB5` | +4 | ok | atlas MOVE_SUMMON (manual); +4, re-derived from the v84 IDB dispatch |
| `SUMMON_ATTACK` | `0xB2` | `0xB6` | +4 | ok | atlas SUMMON_ATTACK (manual); +4, re-derived from the v84 IDB dispatch |
| `DAMAGE_SUMMON` | `0xB3` | `0xB8` | +5 | ok | atlas DAMAGE_SUMMON (manual); +5, re-derived from the v84 IDB dispatch |
| `SUMMON_SKILL` | `0xB4` | `0xB7` | +3 | ok | atlas SUMMON_SKILL (manual); +3, re-derived from the v84 IDB dispatch |
| `SPAWN_DRAGON` | `0xB5` | `0xB9` | +4 | ok | atlas SPAWN_DRAGON (ida-discovered); +4, re-derived from the v84 IDB dispatch |
| `MOVE_DRAGON` | `0xB6` | `0xBA` | +4 | ok | atlas MOVE_DRAGON (ida-discovered); +4, re-derived from the v84 IDB dispatch |
| `REMOVE_DRAGON` | `0xB7` | `0xBB` | +4 | ok | atlas REMOVE_DRAGON (ida-discovered); +4, re-derived from the v84 IDB dispatch |
| `MOVE_PLAYER` | `0xB9` | `0xBD` | +4 | ok | atlas MOVE_PLAYER (manual); +4, re-derived from the v84 IDB dispatch |
| `CLOSE_RANGE_ATTACK` | `0xBA` | `0xBE` | +4 | ok | atlas CLOSE_RANGE_ATTACK (manual); +4, re-derived from the v84 IDB dispatch |
| `RANGED_ATTACK` | `0xBB` | `0xBF` | +4 | ok | atlas RANGED_ATTACK (manual); +4, re-derived from the v84 IDB dispatch |
| `MAGIC_ATTACK` | `0xBC` | `0xC0` | +4 | ok | atlas MAGIC_ATTACK (manual); +4, re-derived from the v84 IDB dispatch |
| `ENERGY_ATTACK` | `0xBD` | `0xC1` | +4 | ok | atlas ENERGY_ATTACK (ida-discovered); +4, re-derived from the v84 IDB dispatch |
| `SKILL_EFFECT` | `0xBE` | `0xC2` | +4 | ok | atlas SKILL_EFFECT (ida-discovered); +4, re-derived from the v84 IDB dispatch |
| `CANCEL_SKILL_EFFECT` | `0xBF` | `0xC3` | +4 | ok | atlas CANCEL_SKILL_EFFECT (ida-discovered); +4, re-derived from the v84 IDB dispatch |
| `DAMAGE_PLAYER` | `0xC0` | `0xC4` | +4 | ok | atlas DAMAGE_PLAYER (ida-discovered); +4, re-derived from the v84 IDB dispatch |
| `FACIAL_EXPRESSION` | `0xC1` | `0xC5` | +4 | ok | atlas FACIAL_EXPRESSION (ida-discovered); +4, re-derived from the v84 IDB dispatch |
| `SHOW_ITEM_EFFECT` | `0xC2` | `0xC6` | +4 | ok | atlas SHOW_ITEM_EFFECT (ida-discovered); +4, re-derived from the v84 IDB dispatch |
| `SHOW_CHAIR` | `0xC4` | `0xC8` | +4 | ok | atlas SHOW_CHAIR (ida-discovered); +4, re-derived from the v84 IDB dispatch |
| `UPDATE_CHAR_LOOK` | `0xC5` | `0xC9` | +4 | ok | atlas UPDATE_CHAR_LOOK (ida-discovered); +4, re-derived from the v84 IDB dispatch |
| `SHOW_FOREIGN_EFFECT` | `0xC6` | `0xCA` | +4 | ok | atlas SHOW_FOREIGN_EFFECT (manual); +4, re-derived from the v84 IDB dispatch |
| `GIVE_FOREIGN_BUFF` | `0xC7` | `0xCB` | +4 | ok | atlas GIVE_FOREIGN_BUFF (manual); +4, re-derived from the v84 IDB dispatch |
| `CANCEL_FOREIGN_BUFF` | `0xC8` | `0xCC` | +4 | ok | atlas CANCEL_FOREIGN_BUFF (ida-discovered); +4, re-derived from the v84 IDB dispatch |
| `UPDATE_PARTYMEMBER_HP` | `0xC9` | `0xCD` | +4 | ok | atlas UPDATE_PARTYMEMBER_HP (ida-discovered); +4, re-derived from the v84 IDB dispatch |
| `GUILD_NAME_CHANGED` | `0xCA` | `0xCE` | +4 | ok | atlas GUILD_NAME_CHANGED (ida-discovered); +4, re-derived from the v84 IDB dispatch |
| `GUILD_MARK_CHANGED` | `0xCB` | `0xCF` | +4 | ok | atlas GUILD_MARK_CHANGED (ida-discovered); +4, re-derived from the v84 IDB dispatch |
| `THROW_GRENADE` | `0xCC` | `0xD0` | +4 | ok | atlas THROW_GRENADE (ida-discovered); +4, re-derived from the v84 IDB dispatch |
| `CANCEL_CHAIR` | `0xCD` | `0xD1` | +4 | ok | atlas CANCEL_CHAIR (ida-discovered); +4, re-derived from the v84 IDB dispatch |
| `SHOW_ITEM_GAIN_INCHAT` | `0xCE` | `0xD2` | +4 | ok | atlas SHOW_ITEM_GAIN_INCHAT (ida-discovered); +4, re-derived from the v84 IDB dispatch |
| `DOJO_WARP_UP` | `0xCF` | `0xD3` | +4 | ok | atlas DOJO_WARP_UP (ida-discovered); +4, re-derived from the v84 IDB dispatch |
| `LUCKSACK_PASS` | `0xD0` | `0xD5` | +5 | ok | atlas LUCKSACK_PASS (ida-discovered); +5, re-derived from the v84 IDB dispatch |
| `LUCKSACK_FAIL` | `0xD1` | `0xD6` | +5 | ok | atlas LUCKSACK_FAIL (ida-discovered); +5, re-derived from the v84 IDB dispatch |
| `MESO_BAG_MESSAGE` | `0xD2` | `0xFFFF` |  | unresolved | no atlas row at v83 0xD2; shift curve ambiguous here: anchors below +5, above +4 |
| `UPDATE_QUEST_INFO` | `0xD3` | `0xD7` | +4 | ok | atlas UPDATE_QUEST_INFO (ida-discovered); +4, re-derived from the v84 IDB dispatch |
| `PLAYER_HINT` | `0xD6` | `0xDA` | +4 | ok | atlas PLAYER_HINT (manual); +4, re-derived from the v84 IDB dispatch |
| `MAKER_RESULT` | `0xD9` | `0xDD` | +4 | ok | atlas MAKER_RESULT (ida-discovered); +4, re-derived from the v84 IDB dispatch |
| `KOREAN_EVENT` | `0xDB` | `0xDF` | +4 | ok | atlas KOREAN_EVENT (ida-discovered); +4, re-derived from the v84 IDB dispatch |
| `OPEN_UI` | `0xDC` | `0xE0` | +4 | ok | atlas OPEN_UI (manual); +4, re-derived from the v84 IDB dispatch |
| `LOCK_UI` | `0xDD` | `0xE2` | +5 | ok | atlas LOCK_UI (manual); +5, re-derived from the v84 IDB dispatch |
| `DISABLE_UI` | `0xDE` | `0xE3` | +5 | ok | atlas DISABLE_UI (manual); +5, re-derived from the v84 IDB dispatch |
| `SPAWN_GUIDE` | `0xDF` | `0xE4` | +5 | ok | atlas SPAWN_GUIDE (ida-discovered); +5, re-derived from the v84 IDB dispatch |
| `TALK_GUIDE` | `0xE0` | `0xE5` | +5 | ok | atlas TALK_GUIDE (manual); +5, re-derived from the v84 IDB dispatch |
| `SHOW_COMBO` | `0xE1` | `0xE6` | +5 | ok | atlas SHOW_COMBO (ida-discovered); +5, re-derived from the v84 IDB dispatch |
| `COOLDOWN` | `0xEA` | `0xF0` | +6 | ok | atlas COOLDOWN (manual); +6, re-derived from the v84 IDB dispatch |
| `SPAWN_MONSTER` | `0xEC` | `0xF2` | +6 | ok | atlas SPAWN_MONSTER (manual); +6, re-derived from the v84 IDB dispatch |
| `KILL_MONSTER` | `0xED` | `0xF3` | +6 | ok | atlas KILL_MONSTER (manual); +6, re-derived from the v84 IDB dispatch |
| `SPAWN_MONSTER_CONTROL` | `0xEE` | `0xF4` | +6 | ok | atlas SPAWN_MONSTER_CONTROL (manual); +6, re-derived from the v84 IDB dispatch |
| `MOVE_MONSTER` | `0xEF` | `0xF5` | +6 | ok | atlas MOVE_MONSTER (manual); +6, re-derived from the v84 IDB dispatch |
| `MOVE_MONSTER_RESPONSE` | `0xF0` | `0xF6` | +6 | ok | atlas MOVE_MONSTER_RESPONSE (manual); +6, re-derived from the v84 IDB dispatch |
| `APPLY_MONSTER_STATUS` | `0xF2` | `0xF8` | +6 | ok | atlas APPLY_MONSTER_STATUS (manual); +6, re-derived from the v84 IDB dispatch |
| `CANCEL_MONSTER_STATUS` | `0xF3` | `0xF9` | +6 | ok | atlas CANCEL_MONSTER_STATUS (manual); +6, re-derived from the v84 IDB dispatch |
| `RESET_MONSTER_ANIMATION` | `0xF4` | `0xFA` | +6 | ok | atlas RESET_MONSTER_ANIMATION (manual); +6, re-derived from the v84 IDB dispatch |
| `DAMAGE_MONSTER` | `0xF6` | `0xFC` | +6 | ok | atlas DAMAGE_MONSTER (ida-discovered); +6, re-derived from the v84 IDB dispatch |
| `ARIANT_THING` | `0xF9` | `0xFF` | +6 | ok | atlas MOB_CRC_KEY_CHANGED (ida-discovered); +6, re-derived from the v84 IDB dispatch |
| `SHOW_MONSTER_HP` | `0xFA` | `0x100` | +6 | ok | atlas SHOW_MONSTER_HP (manual); +6, re-derived from the v84 IDB dispatch |
| `CATCH_MONSTER` | `0xFB` | `0x101` | +6 | ok | atlas CATCH_MONSTER (ida-discovered); +6, re-derived from the v84 IDB dispatch |
| `CATCH_MONSTER_WITH_ITEM` | `0xFC` | `0x102` | +6 | ok | atlas CATCH_MONSTER_WITH_ITEM (ida-discovered); +6, re-derived from the v84 IDB dispatch |
| `SHOW_MAGNET` | `0xFD` | `0x103` | +6 | ok | atlas MOB_SPEAKING (ida-discovered); +6, re-derived from the v84 IDB dispatch |
| `SPAWN_NPC` | `0x101` | `0x108` | +7 | ok | atlas SPAWN_NPC (manual); +7, re-derived from the v84 IDB dispatch |
| `REMOVE_NPC` | `0x102` | `0x109` | +7 | ok | atlas REMOVE_NPC (ida-discovered); +7, re-derived from the v84 IDB dispatch |
| `SPAWN_NPC_REQUEST_CONTROLLER` | `0x103` | `0x10A` | +7 | ok | atlas SPAWN_NPC_REQUEST_CONTROLLER (manual); +7, re-derived from the v84 IDB dispatch |
| `NPC_ACTION` | `0x104` | `0x10B` | +7 | ok | atlas NPC_ACTION (manual); +7, re-derived from the v84 IDB dispatch |
| `SET_NPC_SCRIPTABLE` | `0x107` | `0x10E` | +7 | corrected | atlas SET_NPC_SCRIPTABLE; shift +7 between IDB anchors; 0x10E vacant |
| `SPAWN_HIRED_MERCHANT` | `0x109` | `0x110` | +7 | ok | atlas SPAWN_HIRED_MERCHANT (ida-discovered); +7, re-derived from the v84 IDB dispatch |
| `DESTROY_HIRED_MERCHANT` | `0x10A` | `0x111` | +7 | ok | atlas DESTROY_HIRED_MERCHANT (ida-discovered); +7, re-derived from the v84 IDB dispatch |
| `UPDATE_HIRED_MERCHANT` | `0x10B` | `0x112` | +7 | ok | atlas UPDATE_HIRED_MERCHANT (ida-discovered); +7, re-derived from the v84 IDB dispatch |
| `DROP_ITEM_FROM_MAPOBJECT` | `0x10C` | `0x113` | +7 | ok | atlas DROP_ITEM_FROM_MAPOBJECT (manual); +7, re-derived from the v84 IDB dispatch |
| `REMOVE_ITEM_FROM_MAP` | `0x10D` | `0x114` | +7 | ok | atlas REMOVE_ITEM_FROM_MAP (manual); +7, re-derived from the v84 IDB dispatch |
| `CANNOT_SPAWN_KITE` | `0x10E` | `0x115` | +7 | corrected | atlas CANNOT_SPAWN_KITE; shift +7 between IDB anchors; 0x115 vacant (template still carries the v83 value here, so it is stale too, not evidence) |
| `SPAWN_KITE` | `0x10F` | `0x116` | +7 | corrected | atlas SPAWN_KITE; shift +7 between IDB anchors; 0x116 vacant (template still carries the v83 value here, so it is stale too, not evidence) |
| `REMOVE_KITE` | `0x110` | `0x117` | +7 | ok | atlas REMOVE_KITE (ida-discovered); +7, re-derived from the v84 IDB dispatch |
| `SPAWN_MIST` | `0x111` | `0x118` | +7 | ok | atlas SPAWN_MIST (ida-discovered); +7, re-derived from the v84 IDB dispatch |
| `REMOVE_MIST` | `0x112` | `0x119` | +7 | ok | atlas REMOVE_MIST (ida-discovered); +7, re-derived from the v84 IDB dispatch |
| `SPAWN_DOOR` | `0x113` | `0x11A` | +7 | ok | atlas SPAWN_DOOR (ida-discovered); +7, re-derived from the v84 IDB dispatch |
| `REMOVE_DOOR` | `0x114` | `0x11B` | +7 | ok | atlas REMOVE_DOOR (ida-discovered); +7, re-derived from the v84 IDB dispatch |
| `REACTOR_HIT` | `0x115` | `0x11C` | +7 | ok | atlas REACTOR_HIT (manual); +7, re-derived from the v84 IDB dispatch |
| `REACTOR_SPAWN` | `0x117` | `0x11E` | +7 | ok | atlas REACTOR_SPAWN (manual); +7, re-derived from the v84 IDB dispatch |
| `REACTOR_DESTROY` | `0x118` | `0x11F` | +7 | ok | atlas REACTOR_DESTROY (manual); +7, re-derived from the v84 IDB dispatch |
| `SNOWBALL_STATE` | `0x119` | `0x120` | +7 | ok | atlas SNOWBALL_STATE (csv-import); +7, re-derived from the v84 IDB dispatch |
| `HIT_SNOWBALL` | `0x11A` | `0x121` | +7 | ok | atlas HIT_SNOWBALL (csv-import); +7, re-derived from the v84 IDB dispatch |
| `SNOWBALL_MESSAGE` | `0x11B` | `0x122` | +7 | ok | atlas SNOWBALL_MESSAGE (csv-import); +7, re-derived from the v84 IDB dispatch |
| `LEFT_KNOCK_BACK` | `0x11C` | `0x123` | +7 | ok | atlas LEFT_KNOCK_BACK (ida-discovered); +7, re-derived from the v84 IDB dispatch |
| `COCONUT_HIT` | `0x11D` | `0x124` | +7 | ok | atlas COCONUT_HIT (csv-import); +7, re-derived from the v84 IDB dispatch |
| `COCONUT_SCORE` | `0x11E` | `0x125` | +7 | ok | atlas COCONUT_SCORE (csv-import); +7, re-derived from the v84 IDB dispatch |
| `GUILD_BOSS_HEALER_MOVE` | `0x11F` | `0x126` | +7 | ok | atlas GUILD_BOSS_HEALER_MOVE (csv-import); +7, re-derived from the v84 IDB dispatch |
| `GUILD_BOSS_PULLEY_STATE_CHANGE` | `0x120` | `0x127` | +7 | ok | atlas GUILD_BOSS_PULLEY_STATE_CHANGE (csv-import); +7, re-derived from the v84 IDB dispatch |
| `MONSTER_CARNIVAL_START` | `0x121` | `0x128` | +7 | ok | atlas MONSTER_CARNIVAL_START (ida-discovered); +7, re-derived from the v84 IDB dispatch |
| `MONSTER_CARNIVAL_OBTAINED_CP` | `0x122` | `0x129` | +7 | ok | atlas MONSTER_CARNIVAL_OBTAINED_CP (ida-discovered); +7, re-derived from the v84 IDB dispatch |
| `MONSTER_CARNIVAL_PARTY_CP` | `0x123` | `0x12A` | +7 | ok | atlas MONSTER_CARNIVAL_PARTY_CP (ida-discovered); +7, re-derived from the v84 IDB dispatch |
| `MONSTER_CARNIVAL_SUMMON` | `0x124` | `0x12B` | +7 | ok | atlas MONSTER_CARNIVAL_SUMMON (ida-discovered); +7, re-derived from the v84 IDB dispatch |
| `MONSTER_CARNIVAL_MESSAGE` | `0x125` | `0x12C` | +7 | ok | atlas MONSTER_CARNIVAL_MESSAGE (ida-discovered); +7, re-derived from the v84 IDB dispatch |
| `MONSTER_CARNIVAL_DIED` | `0x126` | `0x12D` | +7 | ok | atlas MONSTER_CARNIVAL_DIED (ida-discovered); +7, re-derived from the v84 IDB dispatch |
| `MONSTER_CARNIVAL_LEAVE` | `0x127` | `0x12E` | +7 | ok | atlas MONSTER_CARNIVAL_LEAVE (ida-discovered); +7, re-derived from the v84 IDB dispatch |
| `ARIANT_ARENA_USER_SCORE` | `0x129` | `0x130` | +7 | ok | atlas ARIANT_ARENA_USER_SCORE (csv-import); +7, re-derived from the v84 IDB dispatch |
| `SHEEP_RANCH_INFO` | `0x12B` | `0x132` | +7 | ok | atlas SHEEP_RANCH_INFO (csv-import); +7, re-derived from the v84 IDB dispatch |
| `SHEEP_RANCH_CLOTHES` | `0x12C` | `0x133` | +7 | ok | atlas SHEEP_RANCH_CLOTHES (csv-import); +7, re-derived from the v84 IDB dispatch |
| `WITCH_TOWER_SCORE_UPDATE` | `0x12D` | `0x134` | +7 | ok | atlas WITCH_TOWER_SCORE_UPDATE (ida-discovered); +7, re-derived from the v84 IDB dispatch |
| `HORNTAIL_CAVE` | `0x12E` | `0x135` | +7 | ok | atlas HORNTAIL_CAVE (csv-import); +7, re-derived from the v84 IDB dispatch |
| `ZAKUM_SHRINE` | `0x12F` | `0x136` | +7 | ok | atlas ZAKUM_SHRINE (csv-import); +7, re-derived from the v84 IDB dispatch |
| `NPC_TALK` | `0x130` | `0x137` | +7 | ok | atlas NPC_TALK (ida-discovered); +7, re-derived from the v84 IDB dispatch |
| `OPEN_NPC_SHOP` | `0x131` | `0x138` | +7 | ok | atlas OPEN_NPC_SHOP (manual); +7, re-derived from the v84 IDB dispatch |
| `CONFIRM_SHOP_TRANSACTION` | `0x132` | `0x139` | +7 | ok | atlas CONFIRM_SHOP_TRANSACTION (manual); +7, re-derived from the v84 IDB dispatch |
| `ADMIN_SHOP_MESSAGE` | `0x133` | `0x13A` | +7 | ok | atlas ADMIN_SHOP_MESSAGE (ida-discovered); +7, re-derived from the v84 IDB dispatch |
| `ADMIN_SHOP` | `0x134` | `0x13B` | +7 | ok | atlas ADMIN_SHOP (ida-discovered); +7, re-derived from the v84 IDB dispatch |
| `STORAGE` | `0x135` | `0x13C` | +7 | ok | atlas STORAGE (manual); +7, re-derived from the v84 IDB dispatch |
| `FREDRICK_MESSAGE` | `0x136` | `0x13D` | +7 | ok | atlas FREDRICK_MESSAGE (ida-discovered); +7, re-derived from the v84 IDB dispatch |
| `FREDRICK` | `0x137` | `0x13E` | +7 | ok | atlas FREDRICK (ida-discovered); +7, re-derived from the v84 IDB dispatch |
| `RPS_GAME` | `0x138` | `0x13F` | +7 | ok | atlas RPS_GAME (ida-discovered); +7, re-derived from the v84 IDB dispatch |
| `MESSENGER` | `0x139` | `0x140` | +7 | ok | atlas MESSENGER (ida-discovered); +7, re-derived from the v84 IDB dispatch |
| `PLAYER_INTERACTION` | `0x13A` | `0x141` | +7 | ok | atlas PLAYER_INTERACTION (ida-discovered); +7, re-derived from the v84 IDB dispatch |
| `TOURNAMENT` | `0x13B` | `0x142` | +7 | ok | atlas TOURNAMENT (manual); +7, re-derived from the v84 IDB dispatch |
| `TOURNAMENT_MATCH_TABLE` | `0x13C` | `0x143` | +7 | ok | atlas TOURNAMENT_MATCH_TABLE (manual); +7, re-derived from the v84 IDB dispatch |
| `TOURNAMENT_SET_PRIZE` | `0x13D` | `0x144` | +7 | ok | atlas TOURNAMENT_SET_PRIZE (manual); +7, re-derived from the v84 IDB dispatch |
| `TOURNAMENT_UEW` | `0x13E` | `0x145` | +7 | ok | atlas TOURNAMENT_UEW (manual); +7, re-derived from the v84 IDB dispatch |
| `TOURNAMENT_CHARACTERS` | `0x13F` | `0x146` | +7 | ok | atlas TOURNAMENT_CHARACTERS (manual); +7, re-derived from the v84 IDB dispatch |
| `WEDDING_PROGRESS` | `0x140` | `0x147` | +7 | ok | atlas WEDDING_PROGRESS (ida-discovered); +7, re-derived from the v84 IDB dispatch |
| `WEDDING_CEREMONY_END` | `0x141` | `0x148` | +7 | ok | atlas WEDDING_CEREMONY_END (ida-discovered); +7, re-derived from the v84 IDB dispatch |
| `PARCEL` | `0x142` | `0x149` | +7 | ok | atlas PARCEL (ida-discovered); +7, re-derived from the v84 IDB dispatch |
| `CHARGE_PARAM_RESULT` | `0x143` | `0x14A` | +7 | ok | atlas CHARGE_PARAM_RESULT (ida-discovered); +7, re-derived from the v84 IDB dispatch |
| `QUERY_CASH_RESULT` | `0x144` | `0x14B` | +7 | ok | atlas QUERY_CASH_RESULT (manual); +7, re-derived from the v84 IDB dispatch |
| `CASHSHOP_OPERATION` | `0x145` | `0x14C` | +7 | ok | atlas CASHSHOP_OPERATION (manual); +7, re-derived from the v84 IDB dispatch |
| `CASHSHOP_PURCHASE_EXP_CHANGED` | `0x146` | `0x14D` | +7 | ok | atlas CASHSHOP_PURCHASE_EXP_CHANGED (ida-discovered); +7, re-derived from the v84 IDB dispatch |
| `CASHSHOP_GIFT_INFO_RESULT` | `0x147` | `0x14E` | +7 | ok | atlas CASHSHOP_GIFT_INFO_RESULT (ida-discovered); +7, re-derived from the v84 IDB dispatch |
| `CASHSHOP_CHECK_NAME_CHANGE` | `0x148` | `0x14F` | +7 | ok | atlas CASHSHOP_CHECK_NAME_CHANGE (ida-discovered); +7, re-derived from the v84 IDB dispatch |
| `CASHSHOP_CHECK_NAME_CHANGE_POSSIBLE_RESULT` | `0x149` | `0x150` | +7 | ok | atlas CASHSHOP_CHECK_NAME_CHANGE_POSSIBLE_RESULT (ida-discovered); +7, re-derived from the v84 IDB dispatch |
| `CASHSHOP_REGISTER_NEW_CHARACTER_RESULT` | `0x14A` | `0x151` | +7 | ok | atlas CASHSHOP_REGISTER_NEW_CHARACTER_RESULT (csv-import); +7, re-derived from the v84 IDB dispatch |
| `CASHSHOP_CHECK_TRANSFER_WORLD_POSSIBLE_RESULT` | `0x14B` | `0x152` | +7 | ok | atlas CASHSHOP_CHECK_TRANSFER_WORLD_POSSIBLE_RESULT (ida-discovered); +7, re-derived from the v84 IDB dispatch |
| `CASHSHOP_GACHAPON_STAMP_RESULT` | `0x14C` | `0x153` | +7 | ok | atlas CASHSHOP_GACHAPON_STAMP_RESULT (ida-discovered); +7, re-derived from the v84 IDB dispatch |
| `CASHSHOP_CASH_ITEM_GACHAPON_RESULT` | `0x14D` | `0x154` | +7 | ok | atlas CASHSHOP_CASH_ITEM_GACHAPON_RESULT (ida-discovered); +7, re-derived from the v84 IDB dispatch |
| `CASHSHOP_CASH_GACHAPON_OPEN_RESULT` | `0x14E` | `0x155` | +7 | corrected | no atlas v83 row; atlas v84 has a row of this exact name at 0x155 |
| `KEYMAP` | `0x14F` | `0x158` | +9 | ok | atlas KEYMAP (manual); +9, re-derived from the v84 IDB dispatch |
| `AUTO_HP_POT` | `0x150` | `0x159` | +9 | ok | atlas AUTO_HP_POT (manual); +9, re-derived from the v84 IDB dispatch |
| `AUTO_MP_POT` | `0x151` | `0x15A` | +9 | ok | atlas AUTO_MP_POT (manual); +9, re-derived from the v84 IDB dispatch |
| `SEND_TV` | `0x155` | `0x15F` | +10 | ok | atlas SEND_TV (ida-discovered); +10, re-derived from the v84 IDB dispatch |
| `REMOVE_TV` | `0x156` | `0x160` | +10 | ok | atlas REMOVE_TV (ida-discovered); +10, re-derived from the v84 IDB dispatch |
| `ENABLE_TV` | `0x157` | `0x161` | +10 | ok | atlas ENABLE_TV (ida-discovered); +10, re-derived from the v84 IDB dispatch |
| `MTS_OPERATION2` | `0x15B` | `0x165` | +10 | corrected | atlas MTS_OPERATION2; shift +10 between IDB anchors; 0x165 vacant |
| `MTS_OPERATION` | `0x15C` | `0x166` | +10 | corrected | atlas MTS_OPERATION; shift +10 between IDB anchors; 0x166 vacant |
| `MAPLELIFE_RESULT` | `0x15D` | `0x167` | +10 | corrected | atlas MAPLELIFE_RESULT; shift +10 between IDB anchors; 0x167 vacant |
| `MAPLELIFE_ERROR` | `0x15E` | `0x168` | +10 | corrected | atlas MAPLELIFE_ERROR; shift +10 between IDB anchors; 0x168 vacant |
| `VICIOUS_HAMMER` | `0x162` | `0x16C` | +10 | ok | atlas VICIOUS_HAMMER (ida-discovered); +10, re-derived from the v84 IDB dispatch |
| `VEGA_SCROLL` | `0x166` | `0x170` | +10 | ok | atlas VEGA_SCROLL (ida-discovered); +10, re-derived from the v84 IDB dispatch |

## recvops (serverbound) - 178 keys

| key | v83 | v84 | delta | status | evidence |
|---|---|---|---|---|---|
| `LOGIN_PASSWORD` | `0x01` | `0x01` | +0 | ok | atlas LOGIN_PASSWORD (csv-import); inside the 0x00-0x3E band ticket 20 proved unchanged |
| `GUEST_LOGIN` | `0x02` | `0x02` | +0 | ok | atlas GUEST_LOGIN (csv-import); inside the 0x00-0x3E band ticket 20 proved unchanged |
| `SERVERLIST_REREQUEST` | `0x04` | `0x04` | +0 | ok | atlas SERVERLIST_REREQUEST (csv-import); inside the 0x00-0x3E band ticket 20 proved unchanged |
| `CHARLIST_REQUEST` | `0x05` | `0x05` | +0 | ok | atlas CHARLIST_REQUEST (csv-import); inside the 0x00-0x3E band ticket 20 proved unchanged |
| `SERVERSTATUS_REQUEST` | `0x06` | `0x06` | +0 | ok | atlas SERVERSTATUS_REQUEST (csv-import); inside the 0x00-0x3E band ticket 20 proved unchanged |
| `ACCEPT_TOS` | `0x07` | `0x07` | +0 | ok | atlas ACCEPT_TOS (csv-import); inside the 0x00-0x3E band ticket 20 proved unchanged |
| `SET_GENDER` | `0x08` | `0x08` | +0 | ok | atlas SET_GENDER (csv-import); inside the 0x00-0x3E band ticket 20 proved unchanged |
| `AFTER_LOGIN` | `0x09` | `0x09` | +0 | ok | atlas AFTER_LOGIN (csv-import); inside the 0x00-0x3E band ticket 20 proved unchanged |
| `REGISTER_PIN` | `0x0A` | `0x0A` | +0 | ok | atlas REGISTER_PIN (csv-import); inside the 0x00-0x3E band ticket 20 proved unchanged |
| `SERVERLIST_REQUEST` | `0x0B` | `0x0B` | +0 | ok | atlas SERVERLIST_REQUEST (csv-import); inside the 0x00-0x3E band ticket 20 proved unchanged |
| `PLAYER_DC` | `0x0C` | `0x0C` | +0 | ok | atlas PLAYER_DC (csv-import); inside the 0x00-0x3E band ticket 20 proved unchanged |
| `VIEW_ALL_CHAR` | `0x0D` | `0x0D` | +0 | ok | atlas VIEW_ALL_CHAR (csv-import); inside the 0x00-0x3E band ticket 20 proved unchanged |
| `PICK_ALL_CHAR` | `0x0E` | `0x0E` | +0 | ok | atlas PICK_ALL_CHAR (csv-import); inside the 0x00-0x3E band ticket 20 proved unchanged |
| `NAME_TRANSFER` | `0x10` | `0x10` | +0 | ok | atlas NAME_TRANSFER (csv-import); inside the 0x00-0x3E band ticket 20 proved unchanged |
| `WORLD_TRANSFER` | `0x12` | `0x12` | +0 | ok | atlas WORLD_TRANSFER (csv-import); inside the 0x00-0x3E band ticket 20 proved unchanged |
| `CHAR_SELECT` | `0x13` | `0x13` | +0 | ok | atlas CHAR_SELECT (csv-import); inside the 0x00-0x3E band ticket 20 proved unchanged |
| `PLAYER_LOGGEDIN` | `0x14` | `0x14` | +0 | ok | atlas PLAYER_LOGGEDIN (csv-import); inside the 0x00-0x3E band ticket 20 proved unchanged |
| `CHECK_CHAR_NAME` | `0x15` | `0x15` | +0 | ok | atlas CHECK_CHAR_NAME (csv-import); inside the 0x00-0x3E band ticket 20 proved unchanged |
| `CREATE_CHAR` | `0x16` | `0x16` | +0 | ok | atlas CREATE_CHAR (csv-import); inside the 0x00-0x3E band ticket 20 proved unchanged |
| `DELETE_CHAR` | `0x17` | `0x17` | +0 | ok | atlas DELETE_CHAR (csv-import); inside the 0x00-0x3E band ticket 20 proved unchanged |
| `PONG` | `0x18` | `0x18` | +0 | ok | atlas PONG (csv-import); inside the 0x00-0x3E band ticket 20 proved unchanged |
| `CLIENT_START_ERROR` | `0x19` | `0x19` | +0 | ok | atlas CLIENT_START_ERROR (csv-import); inside the 0x00-0x3E band ticket 20 proved unchanged |
| `CLIENT_ERROR` | `0x1A` | `0x1A` | +0 | ok | atlas CLIENT_ERROR (csv-import); inside the 0x00-0x3E band ticket 20 proved unchanged |
| `STRANGE_DATA` | `0x1B` | `0x1B` | +0 | ok | atlas STRANGE_DATA (csv-import); inside the 0x00-0x3E band ticket 20 proved unchanged |
| `RELOG` | `0x1C` | `0x1C` | +0 | ok | atlas RELOG (csv-import); inside the 0x00-0x3E band ticket 20 proved unchanged |
| `REGISTER_PIC` | `0x1D` | `0x1D` | +0 | ok | atlas REGISTER_PIC (csv-import); inside the 0x00-0x3E band ticket 20 proved unchanged |
| `CHAR_SELECT_WITH_PIC` | `0x1E` | `0x1E` | +0 | ok | atlas CHAR_SELECT_WITH_PIC (csv-import); inside the 0x00-0x3E band ticket 20 proved unchanged |
| `VIEW_ALL_PIC_REGISTER` | `0x1F` | `0x1F` | +0 | ok | atlas VIEW_ALL_PIC_REGISTER (csv-import); inside the 0x00-0x3E band ticket 20 proved unchanged |
| `VIEW_ALL_WITH_PIC` | `0x20` | `0x20` | +0 | ok | atlas VIEW_ALL_WITH_PIC (csv-import); inside the 0x00-0x3E band ticket 20 proved unchanged |
| `CHANGE_MAP` | `0x26` | `0x26` | +0 | ok | atlas CHANGE_MAP (csv-import); inside the 0x00-0x3E band ticket 20 proved unchanged |
| `CHANGE_CHANNEL` | `0x27` | `0x27` | +0 | ok | atlas CHANGE_CHANNEL (csv-import); inside the 0x00-0x3E band ticket 20 proved unchanged |
| `ENTER_CASHSHOP` | `0x28` | `0x28` | +0 | ok | atlas ENTER_CASHSHOP (csv-import); inside the 0x00-0x3E band ticket 20 proved unchanged |
| `MOVE_PLAYER` | `0x29` | `0x29` | +0 | ok | atlas MOVE_PLAYER (csv-import); inside the 0x00-0x3E band ticket 20 proved unchanged |
| `CANCEL_CHAIR` | `0x2A` | `0x2A` | +0 | ok | atlas CANCEL_CHAIR (csv-import); inside the 0x00-0x3E band ticket 20 proved unchanged |
| `USE_CHAIR` | `0x2B` | `0x2B` | +0 | ok | atlas USE_CHAIR (csv-import); inside the 0x00-0x3E band ticket 20 proved unchanged |
| `CLOSE_RANGE_ATTACK` | `0x2C` | `0x2C` | +0 | ok | atlas CLOSE_RANGE_ATTACK (csv-import); inside the 0x00-0x3E band ticket 20 proved unchanged |
| `RANGED_ATTACK` | `0x2D` | `0x2D` | +0 | ok | atlas RANGED_ATTACK (csv-import); inside the 0x00-0x3E band ticket 20 proved unchanged |
| `MAGIC_ATTACK` | `0x2E` | `0x2E` | +0 | ok | atlas MAGIC_ATTACK (csv-import); inside the 0x00-0x3E band ticket 20 proved unchanged |
| `TOUCH_MONSTER_ATTACK` | `0x2F` | `0x2F` | +0 | ok | atlas TOUCH_MONSTER_ATTACK (csv-import); inside the 0x00-0x3E band ticket 20 proved unchanged |
| `TAKE_DAMAGE` | `0x30` | `0x30` | +0 | ok | atlas TAKE_DAMAGE (csv-import); inside the 0x00-0x3E band ticket 20 proved unchanged |
| `GENERAL_CHAT` | `0x31` | `0x31` | +0 | ok | atlas GENERAL_CHAT (csv-import); inside the 0x00-0x3E band ticket 20 proved unchanged |
| `CLOSE_CHALKBOARD` | `0x32` | `0x32` | +0 | ok | atlas CLOSE_CHALKBOARD (csv-import); inside the 0x00-0x3E band ticket 20 proved unchanged |
| `FACE_EXPRESSION` | `0x33` | `0x33` | +0 | ok | atlas FACE_EXPRESSION (csv-import); inside the 0x00-0x3E band ticket 20 proved unchanged |
| `USE_ITEMEFFECT` | `0x34` | `0x34` | +0 | ok | atlas USE_ITEMEFFECT (csv-import); inside the 0x00-0x3E band ticket 20 proved unchanged |
| `USE_DEATHITEM` | `0x35` | `0x35` | +0 | ok | atlas USE_DEATHITEM (csv-import); inside the 0x00-0x3E band ticket 20 proved unchanged |
| `MOB_BANISH_PLAYER` | `0x38` | `0x38` | +0 | ok | atlas MOB_BANISH_PLAYER (csv-import); inside the 0x00-0x3E band ticket 20 proved unchanged |
| `MONSTER_BOOK_COVER` | `0x39` | `0x39` | +0 | ok | atlas MONSTER_BOOK_COVER (ida-discovered); inside the 0x00-0x3E band ticket 20 proved unchanged |
| `NPC_TALK` | `0x3A` | `0x3A` | +0 | ok | atlas NPC_TALK (csv-import); inside the 0x00-0x3E band ticket 20 proved unchanged |
| `REMOTE_STORE` | `0x3B` | `0x3B` | +0 | ok | atlas REMOTE_STORE (csv-import); inside the 0x00-0x3E band ticket 20 proved unchanged |
| `NPC_TALK_MORE` | `0x3C` | `0x3C` | +0 | ok | atlas NPC_TALK_MORE (csv-import); inside the 0x00-0x3E band ticket 20 proved unchanged |
| `NPC_SHOP` | `0x3D` | `0x3D` | +0 | ok | atlas NPC_SHOP (csv-import); inside the 0x00-0x3E band ticket 20 proved unchanged |
| `STORAGE` | `0x3E` | `0x3E` | +0 | ok | atlas STORAGE (csv-import); inside the 0x00-0x3E band ticket 20 proved unchanged |
| `HIRED_MERCHANT_REQUEST` | `0x3F` | `0x3F` | +0 | ok | atlas HIRED_MERCHANT_REQUEST (csv-import); unchanged, and the live v84 routing table puts it here too |
| `FREDRICK_ACTION` | `0x40` | `0x40` | +0 | confirmed | atlas FREDRICK_ACTION; shift +0 between IDB anchors; 0x40 vacant |
| `DUEY_ACTION` | `0x41` | `0x41` | +0 | confirmed | atlas DUEY_ACTION; shift +0 between IDB anchors; 0x41 vacant |
| `OWL_ACTION` | `0x42` | `0x42` | +0 | ok | atlas OWL_ACTION (csv-import); unchanged, and the live v84 routing table puts it here too |
| `OWL_WARP` | `0x43` | `0x43` | +0 | ok | atlas OWL_WARP (csv-import); unchanged, and the live v84 routing table puts it here too |
| `ADMIN_SHOP` | `0x44` | `0x44` | +0 | confirmed | atlas ADMIN_SHOP; shift +0 between IDB anchors; 0x44 vacant |
| `ITEM_SORT` | `0x45` | `0x45` | +0 | ok | atlas ITEM_SORT (csv-import); unchanged, and the live v84 routing table puts it here too |
| `ITEM_SORT2` | `0x46` | `0x46` | +0 | ok | atlas ITEM_SORT2 (csv-import); unchanged, and the live v84 routing table puts it here too |
| `ITEM_MOVE` | `0x47` | `0x47` | +0 | ok | atlas ITEM_MOVE (csv-import); unchanged, and the live v84 routing table puts it here too |
| `USE_ITEM` | `0x48` | `0x48` | +0 | ok | atlas USE_ITEM (csv-import); unchanged, and the live v84 routing table puts it here too |
| `CANCEL_ITEM_EFFECT` | `0x49` | `0x49` | +0 | ok | atlas CANCEL_ITEM_EFFECT (csv-import); unchanged, and the live v84 routing table puts it here too |
| `USE_SUMMON_BAG` | `0x4B` | `0x4B` | +0 | ok | atlas USE_SUMMON_BAG (csv-import); unchanged, and the live v84 routing table puts it here too |
| `PET_FOOD` | `0x4C` | `0x4C` | +0 | ok | atlas PET_FOOD (csv-import); unchanged, and the live v84 routing table puts it here too |
| `USE_MOUNT_FOOD` | `0x4D` | `0x4D` | +0 | ok | atlas USE_MOUNT_FOOD (csv-import); unchanged, and the live v84 routing table puts it here too |
| `SCRIPTED_ITEM` | `0x4E` | `0x4E` | +0 | ok | atlas SCRIPTED_ITEM (csv-import); unchanged, and the live v84 routing table puts it here too |
| `USE_CASH_ITEM` | `0x4F` | `0x4F` | +0 | ok | atlas USE_CASH_ITEM (csv-import); unchanged, and the live v84 routing table puts it here too |
| `USE_CATCH_ITEM` | `0x51` | `0x51` | +0 | ok | atlas USE_CATCH_ITEM (csv-import); unchanged, and the live v84 routing table puts it here too |
| `USE_SKILL_BOOK` | `0x52` | `0x52` | +0 | ok | atlas USE_SKILL_BOOK (csv-import); unchanged, and the live v84 routing table puts it here too |
| `USE_TELEPORT_ROCK` | `0x54` | `0x54` | +0 | ok | atlas USE_TELEPORT_ROCK (csv-import); unchanged, and the live v84 routing table puts it here too |
| `USE_RETURN_SCROLL` | `0x55` | `0x55` | +0 | ok | atlas USE_RETURN_SCROLL (csv-import); unchanged, and the live v84 routing table puts it here too |
| `USE_UPGRADE_SCROLL` | `0x56` | `0x56` | +0 | ok | atlas USE_UPGRADE_SCROLL (csv-import); unchanged, and the live v84 routing table puts it here too |
| `DISTRIBUTE_AP` | `0x57` | `0x57` | +0 | confirmed | atlas DISTRIBUTE_AP; shift +0 between IDB anchors; 0x57 vacant |
| `AUTO_DISTRIBUTE_AP` | `0x58` | `0x58` | +0 | confirmed | atlas AUTO_DISTRIBUTE_AP; shift +0 between IDB anchors; 0x58 vacant |
| `HEAL_OVER_TIME` | `0x59` | `0x59` | +0 | ok | atlas HEAL_OVER_TIME (csv-import); unchanged, and the live v84 routing table puts it here too |
| `DISTRIBUTE_SP` | `0x5A` | `0x5A` | +0 | ok | atlas DISTRIBUTE_SP (csv-import); unchanged, and the live v84 routing table puts it here too |
| `SPECIAL_MOVE` | `0x5B` | `0x5B` | +0 | ok | atlas SPECIAL_MOVE (csv-import); unchanged, and the live v84 routing table puts it here too |
| `CANCEL_BUFF` | `0x5C` | `0x5C` | +0 | ok | atlas CANCEL_BUFF (csv-import); unchanged, and the live v84 routing table puts it here too |
| `SKILL_EFFECT` | `0x5D` | `0x5D` | +0 | ok | atlas SKILL_EFFECT (csv-import); unchanged, and the live v84 routing table puts it here too |
| `MESO_DROP` | `0x5E` | `0x5E` | +0 | ok | atlas MESO_DROP (csv-import); unchanged, and the live v84 routing table puts it here too |
| `GIVE_FAME` | `0x5F` | `0x5F` | +0 | ok | atlas GIVE_FAME (csv-import); unchanged, and the live v84 routing table puts it here too |
| `CHAR_INFO_REQUEST` | `0x61` | `0x61` | +0 | ok | atlas CHAR_INFO_REQUEST (csv-import); unchanged, and the live v84 routing table puts it here too |
| `SPAWN_PET` | `0x62` | `0x62` | +0 | confirmed | atlas SPAWN_PET; shift +0 between IDB anchors; 0x62 vacant |
| `CANCEL_DEBUFF` | `0x63` | `0x63` | +0 | ok | atlas CANCEL_DEBUFF (csv-import); unchanged, and the live v84 routing table puts it here too |
| `CHANGE_MAP_SPECIAL` | `0x64` | `0x64` | +0 | ok | atlas CHANGE_MAP_SPECIAL (csv-import); unchanged, and the live v84 routing table puts it here too |
| `USE_INNER_PORTAL` | `0x65` | `0x65` | +0 | confirmed | atlas USE_INNER_PORTAL; shift +0 between IDB anchors; 0x65 vacant |
| `TROCK_ADD_MAP` | `0x66` | `0x66` | +0 | ok | atlas TROCK_ADD_MAP (csv-import); unchanged, and the live v84 routing table puts it here too |
| `REPORT` | `0x6A` | `0x6A` | +0 | ok | atlas CLAIM_REQUEST (ida-discovered); unchanged, and the live v84 routing table puts it here too |
| `QUEST_ACTION` | `0x6B` | `0x6B` | +0 | ok | atlas QUEST_ACTION (csv-import); unchanged, and the live v84 routing table puts it here too |
| `GRENADE_EFFECT` | `0x6D` | `0x6D` | +0 | confirmed | atlas GRENADE_EFFECT; shift +0 between IDB anchors; 0x6D vacant |
| `SKILL_MACRO` | `0x6E` | `0x6E` | +0 | ok | atlas SKILL_MACRO (csv-import); unchanged, and the live v84 routing table puts it here too |
| `USE_ITEM_REWARD` | `0x70` | `0x70` | +0 | ok | atlas LOTTERY_ITEM_USE_REQUEST (csv-import); unchanged, and the live v84 routing table puts it here too |
| `MAKER_SKILL` | `0x71` | `0x71` | +0 | confirmed | atlas MAKER_SKILL; shift +0 between IDB anchors; 0x71 vacant |
| `USE_REMOTE` | `0x74` | `0x74` | +0 | confirmed | atlas USE_REMOTE; shift +0 between IDB anchors; 0x74 vacant |
| `WATER_OF_LIFE` | `0x75` | `0x75` | +0 | ok | atlas WATER_OF_LIFE (csv-import); unchanged, and the live v84 routing table puts it here too |
| `ADMIN_CHAT` | `0x76` | `0x78` | +2 | ok | atlas ADMIN_CHAT (ida-discovered); +2, re-derived from the v84 IDB dispatch |
| `MULTI_CHAT` | `0x77` | `0x79` | +2 | ok | atlas MULTI_CHAT (ida-discovered); +2, re-derived from the v84 IDB dispatch |
| `WHISPER` | `0x78` | `0x7A` | +2 | ok | atlas WHISPER (ida-discovered); +2, re-derived from the v84 IDB dispatch |
| `SPOUSE_CHAT` | `0x79` | `0x7B` | +2 | ok | atlas SPOUSE_CHAT (ida-discovered); +2, re-derived from the v84 IDB dispatch |
| `MESSENGER` | `0x7A` | `0x7C` | +2 | ok | atlas MESSENGER (ida-discovered); +2, re-derived from the v84 IDB dispatch |
| `PLAYER_INTERACTION` | `0x7B` | `0x7D` | +2 | ok | atlas PLAYER_INTERACTION (ida-discovered); +2, re-derived from the v84 IDB dispatch |
| `PARTY_OPERATION` | `0x7C` | `0x7E` | +2 | ok | atlas PARTY_OPERATION (ida-discovered); +2, re-derived from the v84 IDB dispatch |
| `DENY_PARTY_REQUEST` | `0x7D` | `0x7F` | +2 | ok | atlas DENY_PARTY_REQUEST (ida-discovered); +2, re-derived from the v84 IDB dispatch |
| `GUILD_OPERATION` | `0x7E` | `0x82` | +4 | ok | atlas GUILD_OPERATION (ida-discovered); +4, re-derived from the v84 IDB dispatch |
| `DENY_GUILD_REQUEST` | `0x7F` | `0x83` | +4 | ok | atlas DENY_GUILD_REQUEST (ida-discovered); +4, re-derived from the v84 IDB dispatch |
| `ADMIN_COMMAND` | `0x80` | `0x84` | +4 | ok | atlas ADMIN_COMMAND (ida-discovered); +4, re-derived from the v84 IDB dispatch |
| `ADMIN_LOG` | `0x81` | `0x85` | +4 | ok | atlas ADMIN_LOG (ida-discovered); +4, re-derived from the v84 IDB dispatch |
| `BUDDYLIST_MODIFY` | `0x82` | `0x86` | +4 | ok | atlas BUDDYLIST_MODIFY (ida-discovered); +4, re-derived from the v84 IDB dispatch |
| `NOTE_ACTION` | `0x83` | `0x87` | +4 | ok | atlas NOTE_ACTION (ida-discovered); +4, re-derived from the v84 IDB dispatch |
| `USE_DOOR` | `0x85` | `0x89` | +4 | ok | atlas USE_DOOR (ida-discovered); +4, re-derived from the v84 IDB dispatch |
| `CHANGE_KEYMAP` | `0x87` | `0x8B` | +4 | ok | atlas CHANGE_KEYMAP (ida-discovered); +4, re-derived from the v84 IDB dispatch |
| `RPS_ACTION` | `0x88` | `0x8C` | +4 | ok | atlas RPS_ACTION (ida-discovered); +4, re-derived from the v84 IDB dispatch |
| `RING_ACTION` | `0x89` | `0x8D` | +4 | ok | atlas RING_ACTION (ida-discovered); +4, re-derived from the v84 IDB dispatch |
| `WEDDING_ACTION` | `0x8A` | `0x8E` | +4 | ok | atlas WEDDING_WISH_LIST_REQUEST (ida-discovered); +4, re-derived from the v84 IDB dispatch |
| `WEDDING_TALK` | `0x8B` | `0x8F` | +4 | ok | atlas WEDDING_ACTION (ida-discovered); +4, re-derived from the v84 IDB dispatch |
| `WEDDING_TALK_MORE` | `0x8B` | `0x8F` | +4 | ok | atlas WEDDING_ACTION (ida-discovered); +4, re-derived from the v84 IDB dispatch |
| `ALLIANCE_OPERATION` | `0x8F` | `0x93` | +4 | ok | atlas ALLIANCE_OPERATION (ida-discovered); +4, re-derived from the v84 IDB dispatch |
| `DENY_ALLIANCE_REQUEST` | `0x90` | `0x94` | +4 | ok | atlas DENY_ALLIANCE_REQUEST (ida-discovered); +4, re-derived from the v84 IDB dispatch |
| `OPEN_FAMILY_PEDIGREE` | `0x91` | `0x95` | +4 | ok | atlas OPEN_FAMILY_PEDIGREE (ida-discovered); +4, re-derived from the v84 IDB dispatch |
| `OPEN_FAMILY` | `0x92` | `0x96` | +4 | ok | atlas OPEN_FAMILY (ida-discovered); +4, re-derived from the v84 IDB dispatch |
| `ADD_FAMILY` | `0x93` | `0x97` | +4 | ok | atlas ADD_FAMILY (ida-discovered); +4, re-derived from the v84 IDB dispatch |
| `SEPARATE_FAMILY_BY_SENIOR` | `0x94` | `0x98` | +4 | ok | atlas SEPARATE_FAMILY_BY_SENIOR (ida-discovered); +4, re-derived from the v84 IDB dispatch |
| `SEPARATE_FAMILY_BY_JUNIOR` | `0x95` | `0x99` | +4 | ok | atlas SEPARATE_FAMILY_BY_JUNIOR (ida-discovered); +4, re-derived from the v84 IDB dispatch |
| `ACCEPT_FAMILY` | `0x96` | `0x9A` | +4 | ok | atlas ACCEPT_FAMILY (ida-discovered); +4, re-derived from the v84 IDB dispatch |
| `USE_FAMILY` | `0x97` | `0x9B` | +4 | ok | atlas USE_FAMILY (ida-discovered); +4, re-derived from the v84 IDB dispatch |
| `CHANGE_FAMILY_MESSAGE` | `0x98` | `0x9C` | +4 | ok | atlas CHANGE_FAMILY_MESSAGE (ida-discovered); +4, re-derived from the v84 IDB dispatch |
| `FAMILY_SUMMON_RESPONSE` | `0x99` | `0x9D` | +4 | ok | atlas FAMILY_SUMMON_RESPONSE (ida-discovered); +4, re-derived from the v84 IDB dispatch |
| `BBS_OPERATION` | `0x9B` | `0x9F` | +4 | ok | atlas BBS_OPERATION (ida-discovered); +4, re-derived from the v84 IDB dispatch |
| `ENTER_MTS` | `0x9C` | `0xA0` | +4 | ok | atlas ENTER_MTS (ida-discovered); +4, re-derived from the v84 IDB dispatch |
| `USE_SOLOMON_ITEM` | `0x9D` | `0xA1` | +4 | ok | atlas USE_SOLOMON_ITEM (ida-discovered); +4, re-derived from the v84 IDB dispatch |
| `USE_GACHA_EXP` | `0x9E` | `0xA2` | +4 | ok | atlas USE_GACHA_EXP (ida-discovered); +4, re-derived from the v84 IDB dispatch |
| `NEW_YEAR_CARD_REQUEST` | `0x9F` | `0xA3` | +4 | ok | atlas NEW_YEAR_CARD_REQUEST (ida-discovered); +4, re-derived from the v84 IDB dispatch |
| `CASHSHOP_SURPRISE` | `0xA1` | `0xA5` | +4 | ok | atlas CASH_ITEM_GACHAPON_BUTTON (ida-discovered); +4, re-derived from the v84 IDB dispatch |
| `CLICK_GUIDE` | `0xA2` | `0xFFFF` |  | unresolved | no atlas row at v83 0xA2; shift curve ambiguous here: anchors below +4, above +6 |
| `ARAN_COMBO_COUNTER` | `0xA3` | `0xA9` | +6 | ok | atlas ARAN_COMBO_COUNTER (ida-discovered); +6, re-derived from the v84 IDB dispatch |
| `MOVE_PET` | `0xA7` | `0xAC` | +5 | ok | atlas MOVE_PET (manual); +5, re-derived from the v84 IDB dispatch |
| `PET_CHAT` | `0xA8` | `0xAD` | +5 | ok | atlas PET_CHAT (manual); +5, re-derived from the v84 IDB dispatch |
| `PET_COMMAND` | `0xA9` | `0xAE` | +5 | ok | atlas PET_COMMAND (manual); +5, re-derived from the v84 IDB dispatch |
| `PET_LOOT` | `0xAA` | `0xAF` | +5 | ok | atlas PET_LOOT (manual); +5, re-derived from the v84 IDB dispatch |
| `PET_AUTO_POT` | `0xAB` | `0xB0` | +5 | ok | atlas PET_AUTO_POT (manual); +5, re-derived from the v84 IDB dispatch |
| `PET_EXCLUDE_ITEMS` | `0xAC` | `0xB1` | +5 | ok | atlas PET_EXCLUDE_ITEMS (manual); +5, re-derived from the v84 IDB dispatch |
| `MOVE_SUMMON` | `0xAF` | `0xB2` | +3 | ok | atlas MOVE_SUMMON (manual); +3, re-derived from the v84 IDB dispatch |
| `SUMMON_ATTACK` | `0xB0` | `0xB3` | +3 | ok | atlas SUMMON_ATTACK (manual); +3, re-derived from the v84 IDB dispatch |
| `DAMAGE_SUMMON` | `0xB1` | `0xB4` | +3 | ok | atlas DAMAGE_SUMMON (manual); +3, re-derived from the v84 IDB dispatch |
| `BEHOLDER` | `0xB2` | `0xB7` | +5 | ok | atlas BEHOLDER (ida-discovered); +5, re-derived from the v84 IDB dispatch |
| `MOVE_DRAGON` | `0xB5` | `0xBA` | +5 | ok | atlas MOVE_DRAGON (ida-discovered); +5, re-derived from the v84 IDB dispatch |
| `CHANGE_QUICKSLOT` | `0xB7` | `0xBC` | +5 | ok | atlas CHANGE_QUICKSLOT (ida-discovered); +5, re-derived from the v84 IDB dispatch |
| `MOVE_LIFE` | `0xBC` | `0xC1` | +5 | ok | atlas MOVE_LIFE (manual); +5, re-derived from the v84 IDB dispatch |
| `AUTO_AGGRO` | `0xBD` | `0xC2` | +5 | corrected | atlas AUTO_AGGRO; shift +5 between IDB anchors; 0xC2 vacant |
| `FIELD_DAMAGE_MOB` | `0xBF` | `0xC4` | +5 | ok | atlas FIELD_DAMAGE_MOB (manual); +5, re-derived from the v84 IDB dispatch |
| `MOB_DAMAGE_MOB_FRIENDLY` | `0xC0` | `0xC5` | +5 | ok | atlas MOB_DAMAGE_MOB_FRIENDLY (manual); +5, re-derived from the v84 IDB dispatch |
| `MONSTER_BOMB` | `0xC1` | `0xC6` | +5 | ok | atlas MONSTER_BOMB (ida-discovered); +5, re-derived from the v84 IDB dispatch |
| `MOB_DAMAGE_MOB` | `0xC2` | `0xC7` | +5 | ok | atlas MOB_DAMAGE_MOB (manual); +5, re-derived from the v84 IDB dispatch |
| `NPC_ACTION` | `0xC5` | `0xCB` | +6 | ok | atlas NPC_ACTION (manual); +6, re-derived from the v84 IDB dispatch |
| `ITEM_PICKUP` | `0xCA` | `0xD0` | +6 | ok | atlas ITEM_PICKUP (manual); +6, re-derived from the v84 IDB dispatch |
| `DAMAGE_REACTOR` | `0xCD` | `0xD3` | +6 | ok | atlas DAMAGE_REACTOR (manual); +6, re-derived from the v84 IDB dispatch |
| `TOUCHING_REACTOR` | `0xCE` | `0xD4` | +6 | corrected | atlas TOUCHING_REACTOR; shift +6 between IDB anchors; 0xD4 vacant |
| `PLAYER_MAP_TRANSFER` | `0xCF` | `0xD5` | +6 | corrected | atlas PLAYER_MAP_TRANSFER; shift +6 between IDB anchors; 0xD5 vacant |
| `SNOWBALL` | `0xD3` | `0xD9` | +6 | ok | atlas SNOWBALL (ida-discovered); +6, re-derived from the v84 IDB dispatch |
| `LEFT_KNOCKBACK` | `0xD4` | `0xDA` | +6 | ok | atlas LEFT_KNOCKBACK (ida-discovered); +6, re-derived from the v84 IDB dispatch |
| `COCONUT` | `0xD5` | `0xDB` | +6 | ok | atlas COCONUT (ida-discovered); +6, re-derived from the v84 IDB dispatch |
| `MATCH_TABLE` | `0xD6` | `0xDC` | +6 | ok | atlas MATCH_TABLE (ida-discovered); +6, re-derived from the v84 IDB dispatch |
| `MONSTER_CARNIVAL` | `0xDA` | `0xE0` | +6 | ok | atlas MONSTER_CARNIVAL (ida-discovered); +6, re-derived from the v84 IDB dispatch |
| `PARTY_SEARCH_REGISTER` | `0xDC` | `0xE2` | +6 | corrected | no atlas row at v83 0xDC; shift +6 between IDB anchors, 0xE2 vacant |
| `PARTY_SEARCH_START` | `0xDE` | `0xE4` | +6 | corrected | atlas PARTY_SEARCH_START; shift +6 between IDB anchors; 0xE4 vacant |
| `PARTY_SEARCH_UPDATE` | `0xDF` | `0xE5` | +6 | corrected | atlas PARTY_SEARCH_UPDATE; shift +6 between IDB anchors; 0xE5 vacant |
| `CHECK_CASH` | `0xE4` | `0xEA` | +6 | ok | atlas CHECK_CASH (manual); +6, re-derived from the v84 IDB dispatch |
| `CASHSHOP_OPERATION` | `0xE5` | `0xEB` | +6 | ok | atlas CASHSHOP_OPERATION (manual); +6, re-derived from the v84 IDB dispatch |
| `COUPON_CODE` | `0xE6` | `0xEC` | +6 | ok | atlas COUPON_CODE (manual); +6, re-derived from the v84 IDB dispatch |
| `OPEN_ITEMUI` | `0xEC` | `0xFFFF` |  | unresolved | atlas OPEN_ITEMUI; UNRESOLVED: shift curve ambiguous: nearest anchor below is +6, above is +7 |
| `CLOSE_ITEMUI` | `0xED` | `0xFFFF` |  | unresolved | atlas CLOSE_ITEMUI; UNRESOLVED: shift curve ambiguous: nearest anchor below is +6, above is +7 |
| `USE_ITEMUI` | `0xEE` | `0xFFFF` |  | unresolved | atlas USE_ITEMUI; UNRESOLVED: shift curve ambiguous: nearest anchor below is +6, above is +7 |
| `MTS_OPERATION` | `0xFD` | `0x104` | +7 | ok | atlas ITC_OPERATION (ida-discovered); +7, re-derived from the v84 IDB dispatch |
| `USE_MAPLELIFE` | `0x100` | `0x107` | +7 | corrected | no atlas row at v83 0x100; shift +7 between IDB anchors, 0x107 vacant |
| `USE_HAMMER` | `0x104` | `0x10B` | +7 | ok | atlas ITEM_UPGRADE_UPDATE (ida-discovered); +7, re-derived from the v84 IDB dispatch |
| `CUSTOM_PACKET` | `0x3713` | `0x3713` |  | cosmic-internal | Cosmic-internal sentinel, never on the wire; carried over verbatim |
| `MAPLETV` | `0xFFFE` | `0xFFFE` |  | cosmic-internal | Cosmic-internal sentinel, never on the wire; carried over verbatim |

## Registry rows left UNRESOLVED (19)

Stale atlas rows the evidence could not pin down. Every Cosmic key that lands on one of
these carries `0xFFFF` in the emitted table.

| direction | atlas op | v83 | why not resolved |
|---|---|---|---|
| serverbound | `FIND_FRIEND` | `0xA6` | shift curve ambiguous: nearest anchor below is +6, above is +5 |
| serverbound | `REQUEST_FOOTHOLD_INFO` | `0xE1` | shift +6 predicts 0xE7 but UNNAMED_R387 already holds it |
| serverbound | `UNNAMED_R377` | `0xE3` | shift +6 predicts 0xE9 but UNNAMED_R389 already holds it |
| serverbound | `UNNAMED_R387` | `0xE7` | shift curve ambiguous: nearest anchor below is +6, above is +7 |
| serverbound | `UNNAMED_R388` | `0xE8` | shift curve ambiguous: nearest anchor below is +6, above is +7 |
| serverbound | `UNNAMED_R389` | `0xE9` | shift curve ambiguous: nearest anchor below is +6, above is +7 |
| serverbound | `OPEN_ITEMUI` | `0xEC` | shift curve ambiguous: nearest anchor below is +6, above is +7 |
| serverbound | `CLOSE_ITEMUI` | `0xED` | shift curve ambiguous: nearest anchor below is +6, above is +7 |
| serverbound | `USE_ITEMUI` | `0xEE` | shift curve ambiguous: nearest anchor below is +6, above is +7 |
| serverbound | `RAISE_PIECE_PUT_ITEM` | `0xEF` | shift curve ambiguous: nearest anchor below is +6, above is +7 |
| serverbound | `UNNAMED_R397` | `0xF0` | shift curve ambiguous: nearest anchor below is +6, above is +7 |
| serverbound | `UNNAMED_R398` | `0xF1` | shift curve ambiguous: nearest anchor below is +6, above is +7 |
| serverbound | `UNNAMED_R399` | `0xF2` | shift curve ambiguous: nearest anchor below is +6, above is +7 |
| serverbound | `UNNAMED_R400` | `0xF3` | shift curve ambiguous: nearest anchor below is +6, above is +7 |
| serverbound | `REQUEST_AUTH_KEY` | `0xF4` | shift curve ambiguous: nearest anchor below is +6, above is +7 |
| serverbound | `REQUEST_AUTH_KEY_2` | `0xF5` | shift curve ambiguous: nearest anchor below is +6, above is +7 |
| serverbound | `UNNAMED_R403` | `0xF6` | shift curve ambiguous: nearest anchor below is +6, above is +7 |
| serverbound | `UNNAMED_R404` | `0xF7` | shift curve ambiguous: nearest anchor below is +6, above is +7 |
| serverbound | `UNNAMED_R405` | `0xF8` | shift curve ambiguous: nearest anchor below is +6, above is +7 |

## Unmatched: Cosmic keys with no atlas row

| table | key | v83 | outcome |
|---|---|---|---|
| sendops | `MESO_BAG_MESSAGE` | `0xD2` | UNRESOLVED - shift curve ambiguous here: anchors below +5, above +4 |
| sendops | `CASHSHOP_CASH_GACHAPON_OPEN_RESULT` | `0x14E` | resolved to 0x155 by name against a v84-only atlas row |
| recvops | `CLICK_GUIDE` | `0xA2` | UNRESOLVED - shift curve ambiguous here: anchors below +4, above +6 |
| recvops | `PARTY_SEARCH_REGISTER` | `0xDC` | resolved to 0xE2 by the shift curve (+6), target vacant |
| recvops | `USE_MAPLELIFE` | `0x100` | resolved to 0x107 by the shift curve (+7), target vacant |
| recvops | `CUSTOM_PACKET` | `0x3713` | kept verbatim (Cosmic-internal sentinel) |
| recvops | `MAPLETV` | `0xFFFE` | kept verbatim (Cosmic-internal sentinel) |

## Unmatched: atlas rows with no Cosmic key (75)

| direction | atlas op | v84 opcode |
|---|---|---|
| clientbound | `BUFFZONE_EFFECT/SAY_IMAGE` | `0xEE` |
| clientbound | `CHAT_MSG` | `0xED` |
| clientbound | `EVOLVE_PET` | `0xAC` |
| clientbound | `INC_MOB_CHARGE_COUNT` | `0x104` |
| clientbound | `LOGIN_AUTH` | `0x17` |
| clientbound | `MOB_AFFECTED` | `0xFB` |
| clientbound | `MOB_ATTACKED_BY_MOB` | `0x106` |
| clientbound | `MOB_SKILL_DELAY` | `0x105` |
| clientbound | `MONSTER_CARNIVAL_RESULT` | `0x12F` |
| clientbound | `MONSTER_SPECIAL_EFFECT_BY_SKILL` | `0xFD` |
| clientbound | `MTS_CHARGE_PARAM_RESULT` | `0x164` |
| clientbound | `NOTICE_MSG` | `0xEC` |
| clientbound | `NPC_SPECIAL_ACTION` | `0x10D` |
| clientbound | `OPEN_SKILL_GUIDE` | `0xEB` |
| clientbound | `PASS_MATE_NAME` | `0xE9` |
| clientbound | `PLAY_EVENT_SOUND` | `0xDB` |
| clientbound | `PLAY_MINI_GAME_SOUND` | `0xDC` |
| clientbound | `POTION_DISCOUNT_RATE_CHANGED` | `0x60` |
| clientbound | `RADIO_SCHEDULE` | `0xEA` |
| clientbound | `RANDOM_EMOTION` | `0xE7` |
| clientbound | `REACTOR_MOVE` | `0x11D` |
| clientbound | `RESIGN_QUEST_RETURN` | `0xE8` |
| clientbound | `SHOW_UPGRADE_TOMB_EFFECT` | `0xC7` |
| clientbound | `UPDATE_LIMITED_INFO` | `0x10C` |
| serverbound | `ACCOUNT_INFO_REQUEST` | `0x03` |
| serverbound | `ANTI_MACRO_ITEM_USE` | `0x67` |
| serverbound | `ANTI_MACRO_RESULT` | `0x69` |
| serverbound | `ANTI_MACRO_TARGET` | `0x68` |
| serverbound | `BOOBY_TRAP_ALERT` | `0x91` |
| serverbound | `CLIENT_START` | `0x23` |
| serverbound | `DESTROY_PET_ITEM_REQUEST` | `0x50` |
| serverbound | `FIND_FRIEND` | `0xA6` |
| serverbound | `GUILD_BOSS` | `0xDD` |
| serverbound | `ITC_QUERY_CASH_REQUEST` | `0x103` |
| serverbound | `ITC_STATUS_CHARGE` | `0x102` |
| serverbound | `MOB_CRC_KEY_CHANGED_REPLY` | `0xAA` |
| serverbound | `MOB_DROP_PICKUP_REQUEST` | `0xC3` |
| serverbound | `MOB_SKILL_DELAY_END` | `0xC8` |
| serverbound | `MORPH_REQUEST` | `0xA4` |
| serverbound | `NEXON_PASSPORT` | `0x24` |
| serverbound | `NPC_ITEM_USE_REQUEST` | `0x6F` |
| serverbound | `NPC_SPECIAL_ACTION` | `0xCC` |
| serverbound | `PACKET_ERROR` | `0x25` |
| serverbound | `RAISE_PIECE_PUT_ITEM` | `0xEF` |
| serverbound | `REQUEST_AUTH_KEY` | `0xF4` |
| serverbound | `REQUEST_AUTH_KEY_2` | `0xF5` |
| serverbound | `REQUEST_FOOTHOLD_INFO` | `0xE1` |
| serverbound | `STATE_CHANGE_BY_PORTABLE_CHAIR_REQUEST` | `0x4A` |
| serverbound | `SUE_CHARACTER` | `0x72` |
| serverbound | `UNNAMED_R149` | `0x60` |
| serverbound | `UNNAMED_R18` | `0x11` |
| serverbound | `UNNAMED_R346` | `0xCF` |
| serverbound | `UNNAMED_R356` | `0xD7` |
| serverbound | `UNNAMED_R357` | `0xD8` |
| serverbound | `UNNAMED_R363` | `0xDE` |
| serverbound | `UNNAMED_R377` | `0xE3` |
| serverbound | `UNNAMED_R38` | `0x21` |
| serverbound | `UNNAMED_R387` | `0xE7` |
| serverbound | `UNNAMED_R388` | `0xE8` |
| serverbound | `UNNAMED_R389` | `0xE9` |
| serverbound | `UNNAMED_R39` | `0x22` |
| serverbound | `UNNAMED_R397` | `0xF0` |
| serverbound | `UNNAMED_R398` | `0xF1` |
| serverbound | `UNNAMED_R399` | `0xF2` |
| serverbound | `UNNAMED_R400` | `0xF3` |
| serverbound | `UNNAMED_R403` | `0xF6` |
| serverbound | `UNNAMED_R404` | `0xF7` |
| serverbound | `UNNAMED_R405` | `0xF8` |
| serverbound | `UNNAMED_R74` | `0x36` |
| serverbound | `UNNAMED_R75` | `0x37` |
| serverbound | `USER_CALC_DAMAGE_STAT_SET_REQUEST` | `0x6C` |
| serverbound | `USE_GACHAPON_BOX_ITEM` | `0x73` |
| serverbound | `USE_SHOP_SCANNER_ITEM` | `0x53` |
| serverbound | `VAC` | `0x0F` |
| serverbound | `WEDDING_TALK` | `0x90` |
