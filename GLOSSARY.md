# Asset Report Dashboard

Vocabulary for reporting and screening electrical power assets.

**ATDigital Report**:
The reporting application branded with the ATDigital logo.
_Avoid_: ATDigital Tester, IED

## Transformer Fleet

**AliasName**:
The human-readable transformer name stored in `asset_info.asset_name`; it is shown with the serial number when both are available.
_Avoid_: display name, transformer name

**Bay**:
The equipment bay that owns a transformer asset in the electrical hierarchy.
_Avoid_: site

**Transformer Pair**:
Two transformers evaluated together for parallel-operation compatibility. A technical filter retains a pair only when both transformers match it.
_Avoid_: matching transformer, parallel pair

**Ratio**:
The transformer winding voltages ordered as Primary, Secondary, then Tertiary and normalized to kV.
_Avoid_: transformation ratio
