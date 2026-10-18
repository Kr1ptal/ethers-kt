import EthersKt
import Foundation

/// Calls the exported framework the way a Swift app does. Compiling this file is half the test: every name, label,
/// `throws` and closure type below is what the generated header has to provide.
@main
struct SwiftInteropTest {
    static func main() async {
        let harness = Harness()
        do {
            let fixture = try TransferFixture()
            resultTests(harness, fixture)
            solanaTests(harness, fixture)
        } catch {
            harness.fail("fixture setup threw \(error)")
        }
        await confirmationTests(harness)
        harness.finish()
    }
}

struct MissingValue: Error {}

/// Unwrap a Kotlin `Result`, failing on a missing value as well as on a `Failure`.
func value<T: AnyObject, E: AnyObject>(_ result: EthersResult<T, E>?) throws -> T {
    guard let value = try result?.unwrap() else { throw MissingValue() }
    return value
}

/// Unwrap the error of a Kotlin `Result`, failing on a `Success`.
func errorOf<T: AnyObject, E: AnyObject>(_ result: EthersResult<T, E>?) throws -> E? {
    try result?.unwrapError()
}

/// Bob has signed a transfer to Alice, whose signature is still missing.
struct TransferFixture {
    let alice: KeypairSigner
    let bob: KeypairSigner
    let tx: SolanaTxLegacy
    let partial: Data

    init() throws {
        alice = try KeypairSigner.companion.fromSeed(data: Data(repeating: 1, count: 32))
        bob = try KeypairSigner.companion.fromSeed(data: Data(repeating: 2, count: 32))
        let blockhash = try SolanaBlockhash.companion.fromData(data: Data(repeating: 3, count: 32))
        // `lamports:` takes the BigInteger overload; the Int64 one shares its selector, so Kotlin mangles its label
        let transfer = try SystemProgram.shared.transfer(from: bob.publicKey, to: alice.publicKey, lamports_: 42)
        tx = try value(SolanaTxLegacy.companion.compile(feePayer: alice.publicKey, blockhash: blockhash, instruction: transfer))
        let bobSignature = bob.signMessage(message: tx.serializeMessage())
        partial = try tx.signingBuilder().addSignature(signer: bob.publicKey, signature: bobSignature).serializePartialData()
    }
}

func resultTests(_ t: Harness, _ f: TransferFixture) {
    let ok = SolanaTransactionSignedBuilder.companion.deserializePartial(data: f.partial)
    let bad = SolanaTransactionSignedBuilder.companion.deserializePartial(data: Data([1, 2, 3]))

    t.test("isSuccess and isFailure") {
        t.check(ok.isSuccess() && !ok.isFailure(), "ok is a success")
        t.check(bad.isFailure() && !bad.isSuccess(), "bad is a failure")
    }

    t.test("unwrap throws a failure, with the Kotlin error reachable") {
        do {
            _ = try bad.unwrap()
            t.fail("unwrap of a failure returned")
        } catch {
            let exception = (error as NSError).kotlinException as? SolanaTransactionException
            t.check(exception != nil, "kotlinException is a SolanaTransactionException, got \(error)")
        }
    }

    t.test("unwrapError and the OrNull variants") {
        t.check(try bad.unwrapError() != nil, "unwrapError of a failure returns the error")
        t.check(bad.unwrapOrNull() == nil, "unwrapOrNull of a failure is nil")
        t.check(ok.unwrapErrorOrNull() == nil, "unwrapErrorOrNull of a success is nil")
    }

    t.test("onSuccess and onFailure take typed closures") {
        var seen: [String] = []
        ok.onSuccess { (_: SolanaTransactionSignedBuilder?) in seen.append("success") }
        ok.onFailure { (_: SolanaTransactionError?) in seen.append("unexpected failure") }
        bad.onSuccess { (_: SolanaTransactionSignedBuilder?) in seen.append("unexpected success") }
        bad.onFailure { (_: SolanaTransactionError?) in seen.append("failure") }
        t.check(seen == ["success", "failure"], "callbacks fired: \(seen)")
    }

    t.test("unwrapOrElse and unwrapErrorOrElse take typed closures") {
        let builder = try value(ok)
        let fallback: SolanaTransactionSignedBuilder? = bad.unwrapOrElse { (_: SolanaTransactionError?) in builder }
        t.check(fallback === builder, "unwrapOrElse returns the closure's value on failure")
        t.check(ok.unwrapOrElse { _ in nil } != nil, "unwrapOrElse returns the value on success")

        let error = try bad.unwrapError()
        let fallbackError: SolanaTransactionError? = ok.unwrapErrorOrElse { (_: SolanaTransactionSignedBuilder?) in error }
        t.check(fallbackError === error, "unwrapErrorOrElse returns the closure's error on success")
    }
}

func solanaTests(_ t: Harness, _ f: TransferFixture) {
    t.test("README flow: complete a partially signed envelope through Data") {
        let builder = try value(SolanaTransactionSignedBuilder.companion.deserializePartial(data: f.partial))
        let signatureBytes = f.alice.signMessage(message: f.tx.serializeMessage()).toData()
        _ = try builder.addSignature(signer: f.alice.publicKey, signature: SolanaSignature.companion.fromData(data: signatureBytes))
        let signed = try value(builder.build())
        let bytes: Data = signed.serializeData()

        let decoded = try value(SolanaTransactionSigned.companion.deserialize(data: bytes))
        t.check(decoded == signed, "the envelope round trips")
        t.check(decoded.serializeData() == bytes, "re-serializing gives the same bytes")
    }

    t.test("Data inputs of the wrong length throw IllegalArgumentException") {
        for (name, call) in [
            ("address", { _ = try SolanaAddress.companion.fromData(data: Data(count: 31)) }),
            ("signature", { _ = try SolanaSignature.companion.fromData(data: Data(count: 63)) }),
            ("seed", { _ = try KeypairSigner.companion.fromSeed(data: Data(count: 31)) }),
        ] as [(String, () throws -> Void)] {
            do {
                try call()
                t.fail("\(name) accepted a short input")
            } catch {
                t.check((error as NSError).kotlinException is KotlinIllegalArgumentException, "\(name) threw \(error)")
            }
        }
    }

    t.test("an unknown signer throws instead of aborting") {
        let stranger = try KeypairSigner.companion.fromSeed(data: Data(repeating: 9, count: 32))
        let signature = stranger.signMessage(message: f.tx.serializeMessage())
        do {
            _ = try f.tx.signingBuilder().addSignature(signer: stranger.publicKey, signature: signature)
            t.fail("an unknown signer was accepted")
        } catch {
            t.check((error as NSError).kotlinException is KotlinIllegalArgumentException, "threw \(error)")
        }
    }

    t.test("message bytes round trip through Data") {
        // both sides hang off an interface: the encoder is a free function, the decoder a companion reached via `shared`
        let message: Data = SolanaDataKt.serializeMessageData(f.tx)
        let decoded = try SolanaTransactionUnsignedCompanion.shared.deserializeMessage(data: message).unwrap()
        t.check((decoded as? NSObject)?.isEqual(f.tx) == true, "the message decodes to the same transaction")
    }

    t.test("keys and primitives round trip through Data") {
        t.check(try SolanaAddress.companion.fromData(data: f.alice.publicKey.toData()) == f.alice.publicKey, "address")
        t.check(f.alice.publicKey.toData().count == 32, "an address is 32 bytes")
    }
}

func confirmationTests(_ t: Harness) async {
    // Swift runs async code on the cooperative pool, not the main thread; Kotlin suspend functions must allow that.
    await Task.detached {
        await t.testAsync("confirmation overloads are async and default to confirmed") {
            t.check(pthread_main_np() == 0, "runs off the main thread")
            let pending = SwiftFixturesKt.pendingSolanaTransaction(confirmationStatus: "confirmed")
            t.check(try value(await pending.confirmation()).confirmationStatus == Commitment.confirmed, "confirmation()")
            t.check(try value(await pending.confirmation(commitment: Commitment.processed)).confirmationStatus == Commitment.confirmed, "confirmation(commitment:)")
            let status = try value(await pending.confirmation(commitment: Commitment.confirmed, intervalSeconds: 0.001, timeoutSeconds: 5))
            t.check(status.confirmationStatus == Commitment.confirmed, "confirmation(commitment:intervalSeconds:timeoutSeconds:)")
        }

        await t.testAsync("confirmation times out after the given seconds") {
            let pending = SwiftFixturesKt.pendingSolanaTransaction(confirmationStatus: "processed")
            let error = try errorOf(await pending.confirmation(commitment: Commitment.finalized, intervalSeconds: 0.001, timeoutSeconds: 0.05))
            t.check(error is PendingSolanaTransaction.ErrorTimedOut, "timed out, got \(String(describing: error))")
        }

        await t.testAsync("confirmation rejects NaN seconds by throwing") {
            let pending = SwiftFixturesKt.pendingSolanaTransaction(confirmationStatus: "confirmed")
            do {
                _ = try await pending.confirmation(commitment: Commitment.confirmed, intervalSeconds: .nan, timeoutSeconds: 1)
                t.fail("NaN was accepted")
            } catch {
                t.check((error as NSError).kotlinException is KotlinIllegalArgumentException, "threw \(error)")
            }
        }
    }.value
}

final class Harness: @unchecked Sendable {
    private var failures = 0

    func check(_ condition: Bool, _ message: String, line: UInt = #line) {
        if !condition { fail(message, line: line) }
    }

    func fail(_ message: String, line: UInt = #line) {
        failures += 1
        print("  FAIL (Main.swift:\(line)): \(message)")
    }

    func test(_ name: String, _ body: () throws -> Void) {
        print(name)
        do { try body() } catch { fail("threw \(error)") }
    }

    func testAsync(_ name: String, _ body: () async throws -> Void) async {
        print(name)
        do { try await body() } catch { fail("threw \(error)") }
    }

    func finish() -> Never {
        print(failures == 0 ? "All Swift interop checks passed" : "\(failures) Swift interop check(s) failed")
        exit(failures == 0 ? 0 : 1)
    }
}
