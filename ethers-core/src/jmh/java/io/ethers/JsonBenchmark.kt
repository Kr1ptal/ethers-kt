package io.ethers

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonToken
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.deser.std.StdDeserializer
import io.ethers.core.FastHex
import io.ethers.core.Jackson
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.BenchmarkMode
import org.openjdk.jmh.annotations.Fork
import org.openjdk.jmh.annotations.Mode
import org.openjdk.jmh.annotations.OutputTimeUnit
import org.openjdk.jmh.infra.Blackhole
import org.openjdk.jmh.profile.GCProfiler
import org.openjdk.jmh.runner.Runner
import org.openjdk.jmh.runner.options.OptionsBuilder
import java.io.Writer
import java.util.concurrent.TimeUnit
import kotlin.math.max

@Fork(value = 1)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
open class JsonBenchmark {
    @Benchmark
    fun deserializeJacksonReflection(bh: Blackhole) {
        bh.consume(Jackson.MAPPER.readValue(DATA, BlockWithHashesReflection::class.java))
    }

    @Benchmark
    fun deserializeJacksonAnnotations(bh: Blackhole) {
        bh.consume(Jackson.MAPPER.readValue(DATA, BlockWithHashesAnnotations::class.java))
    }

    @Benchmark
    fun deserializeJacksonDeserializer(bh: Blackhole) {
        bh.consume(Jackson.MAPPER.readValue(DATA, BlockWithHashes::class.java))
    }

    @Benchmark
    fun deserializeKotlinSerialization(bh: Blackhole) {
        bh.consume(Json.decodeFromString<BlockWithHashesReflection>(DATA))
    }

    companion object {
        private const val DATA =
            "{\"baseFeePerGas\":\"0x68acb5d7c\",\"difficulty\":\"0x0\",\"extraData\":\"0x6265617665726275696c642e6f7267\",\"gasLimit\":\"0x1c9c380\",\"gasUsed\":\"0xf48ce8\",\"hash\":\"0xba672570225717f87b3adc6c01bc5d285e1f4527c46b285b9d9d1c044b9144ee\",\"logsBloom\":\"0x2aa30b53412c036975af8719a2fcd539b1962f7dfe94b05dc40d2c05f6a4c9275241f754a8dc7c1ac58e1f6820311b6ef605b14cff3539058a6500c2c5b9e3116c24f038fceb592c7b237bfe2504706682d031aea0449f72a8935472ae6168f2d34e9c116a32858e254d980c2d4aec55aadd1ee902996d45cc5a65dcce4bf2d54f01875490a3881192cfbe02c346bd269c42e6e3cfd310adfd6ca8e28ab380289ab08d2281af6171ea0149ebb899fd970e7541134c0a3ceaa5f556e0882805dc7b91555306938d498320e3b27a27fe44ba78d00e57640f94198417eac89aa2bd6333a39ee922ea4f42b7f6949ae4c967a728957a1fee50f0c11d14385ea05541\",\"miner\":\"0x4675c7e5baafbffbca748158becba61ef3b0a263\",\"mixHash\":\"0x31a76baaeb80db67feec25bde9d397f6a7b2bfa13ef79aaa1aeaf20865fad68d\",\"nonce\":\"0x0000000000000000\",\"number\":\"0x10b7b87\",\"parentHash\":\"0x8d210e4f301a94e97ec79b4361584b7b1697caebca7c8de4a4b158a380ff426f\",\"receiptsRoot\":\"0x8898c4d2a8af3e3633c75e3c597e01edc39d14f9c49bdcce857ea80e1b3fa42e\",\"sha3Uncles\":\"0x1dcc4de8dec75d7aab85b567b6ccd41ad312451b948a7413f0a142fd40d49347\",\"size\":\"0x143b8\",\"stateRoot\":\"0x080660f501f1ba66a3b33a27dbeb9097955487d2f0deef21442a63063076e27b\",\"timestamp\":\"0x64933d3f\",\"totalDifficulty\":\"0xc70d815d562d3cfa955\",\"transactions\":[\"0xb91a1f21f103e2a451d699d0501e7ec4aef874c2a7797234c3257daf1037d48b\",\"0xa5e9be4ba03c7497a08c22832319dc541dc0c5798a3d549e40b0269bc7dd523d\",\"0x523bc9b021fdc49498cf5e5a019c9488d45e30b93bbca4d1ae1bcdcb01cb1694\",\"0xc08429917b2329655ee58bdde77df2f80c95305e0f3738defbf50a16d929bdef\",\"0x319e81041223a2cb445ece6b537ced9d863719f1c8e1748fc6b0ef0d5e9f8b82\",\"0x0e36f327e57126bc92a88a283ac7036f23c871b305bf7aefa8d9bb11824f19ce\",\"0x11ded07ba16880519b4d4ccfb749ce839e34a24f94a30d04e5960b2a716c9260\",\"0x9a14a735ae014f0147ffe66c04372a276fd5a3526a1147099298c8c161ae7560\",\"0xd643d06ad58dde5db71323d5cce07aff2a7e5b38ebb4e997621401ef3658dfc9\",\"0xcfddefa74bc89c50b07e92fa625851682dd69f514902b5bad3de6c5cdcb6a2ad\",\"0x9fa3035f8e73f590d676d4ed8a5e4b067bf74351584c99185ff48c6d1c456f63\",\"0x4b8e7520f849f8e20530dbb357291056e7f33986e4e1529df0cb2d3376bea931\",\"0x7e91cab33da22535f1285abfa32625692d19dee044a779518ba3222cce093e7c\",\"0xd59839c9022330d70d03730359ac5269f5d6edf3f9f4dcfe915b7e6fa5c6b05b\",\"0xc20813dccc92a14c40007ad4a1049586523926073120b58924d20ca4a273336a\",\"0x14b83463713e2d2ab5bf90a82c78868561accae3422098134cef38054d57ffa6\",\"0xa372640dfd511659c8d375f7b5fa9e3a983bbbf6a1f96824fb04c25ccc17240b\",\"0x095f2d04cbbb5b21c2fe5f2c0a6342b35107d3ffc59f129c298e58b39de9fc4e\",\"0x1c7df25dd4f285fe5061785185d23b0f03f3c4a3f3991297d498b057a801bfbe\",\"0x46738e3d3f5c80bf93265baf37926abe0f7c1981a146fc0d6d0fc45c1a00f566\",\"0xfd3da3f38aa096eae499d8932408311ec8e697af33afca58ec025ae898cad37c\",\"0x3e8c0dbf08f5fb2947036d0968c55406d00ff9c48772225a82ecc099e37b6b56\",\"0x57d6c0f7c73a3df07defe6792fc0aaa376c4f43140a074d3a00fe274b7d4cbfa\",\"0x22a2f944dbb39ce2507affa81b7d570faeb19705e3f83a9e90b262357b0c4bff\",\"0x1fa123a16f7b439d7d0e1b9bfefd33eda1e337eb98e2dc870e879251ee3a749d\",\"0xa67513b06f363b2fffe8ab20582307171f62b2a12f8507fba00027607f669c5d\",\"0x1709ed3c7db5ffc9aa9bc3205d0eb6b99da6322fc884d63b7dfe4d8354d3caf8\",\"0x11a0c9b97ebaf960fab75438cbcd4c62c36a1727f4933dd475a010f15d8f6842\",\"0x0b01adcf9bf1c9338cc8316e46b489c651c8a199b144b9652ce11b620859d119\",\"0x385c998b11ccffa820f35ffa26f09f2081259696db953640da1bb2d4da24b111\",\"0xf2fe0a72df66633e8d4f9d526b8506d38f782ec04437fcb0b08ae723a1ba8635\",\"0x6155037022c720b3498c605a8f39e9a7b03e07934e62723d1f29d46946776309\",\"0xeab4f022b6ac8eeb56831f3cc89c0c3891bb638ccf453adf11f8740bd5303ee0\",\"0x772936a4981c497661d6d4d31e7cb985d3df6bb12fd69f1e28e7d084bc6d1257\",\"0x312950377c9419fba1520f03a51f818e16f817c068480e9d8ebad9bde8f25693\",\"0x4f6935404aad95f9331faea12db82e7aaa5f46b3b4061c55bcbdefa3096f828f\",\"0x2fbd1604deabd25c714bbad7a1ad02c361af485106561256468ba9d0175b4e8a\",\"0x5db7d5f04388a4bf6bcfcacd4ba1ac59d43492f864a4b8620a5aa8693c3e12ef\",\"0xa99f6e9447337b47887f21dc97e57d8e3d0548f55adb91591e6470dfc94a5a3f\",\"0x8bcce9d55571afa6ed4df74467eb7e33a7fc5ce8df35a71f807e29ec523d240d\",\"0x811c4d5824f9b05f882ae23831d0d6b44b382b31915811e35443789982e391a5\",\"0x1f3649277b725fd52566ce5356181c4ce8fa964a536578a4d1d19da44dfc30f2\",\"0x006e5964b270e03623cc9371c56037d274f06a171e7676fda363946eb0b5582a\",\"0x899e9ad1c30c9fb9112394c1bbc58e4cae349cb9ce5b1bbe177ba4ae73bfb529\",\"0x41db033d32959ff16a8ccb152f1da6d4c929173f52e425bccea99c9fbcc8e45a\",\"0x87f7e2d47a68fd170882243beb268c801e00e5e121e92055dc41a0620b795317\",\"0x9aabb959df2551a55f1de8c7d4a10bbe99c1e2ac01eeb77a504fb417e158e03f\",\"0x3b7115d6864298b6a818dc7ff36488a5f0f0b56f753002597ef5e7cde376735c\",\"0x4ea9c4e78633696ba7aa07cade879b2040fe8404ac7e78532420c8b7bb0aadac\",\"0xd6eb649d8522028daf775f0edb09fdc3b5cb4c5f7ee5dd1da1d128e4d856cda5\",\"0xeb541835ae2d9647e30bd9a26ff27765503b5499927687b210a72b9d7ab952d5\",\"0x80a5a7d33adc5f5bf46b4a078030fc52788282644b7f02ce7b518a556510bcdd\",\"0x3af402767eb58cf58f3c8bd6fe4bf1229f91a196b89e7acb832034ebe7e403ed\",\"0xf431705e35982f34980d06c2c8745b6a4fb568aee87e5cc44caefe4c775b386d\",\"0x66c1987d78ee270e80b6f594948dadf8feb1b3861cf84025246060ed9f4c903c\",\"0xb9aff12ed7bdfa929c8aede31ecfd64e99cfd270d088f0850542352560c3e829\",\"0xf95714346ec9396a290e23be8e9718fa2cc3e3c45f3792d04fa5b8e127ebb7c0\",\"0x2c6888c34df52f73735d23d3d7791869e5bd3209b6f495378f8b9153d5190f57\",\"0xd5b9cd4c5b04cfaba3c57cbdab394ea633ebb7f88c3d01c222e4b112bdc90458\",\"0x54cae1f0f5cfb3d34be532428f64a426ad28b975e8a790eb842b9e7bc0ca832d\",\"0xce80d9c6edbc7eca18082e1a9a584503383e3432a343fa79036813915ae505ca\",\"0x9f14db5925cd727d4c02369185d58f9f92ea597aa4eab8072e4454d9fb4db15c\",\"0x0492ff5a7ca1ce54b09b6e48f60a0af14b6fe57e68a77b646919ff9d4628d76c\",\"0x2c814f63e4dd509de4087e923f4698914b11abda3105fd9e3668cd88bad4ab83\",\"0xf29a205d64354f28b744a82f60c78f8251560905673134279f672324454dc134\",\"0x1e3dba709093aaf5dac6971ae55b360d509e7b69d823f4053d4f43b6e00f7bf8\",\"0xad396496d30b1a4ed872a47777cb4a18b245cb20ea631b5f224aed1fa0b2703c\",\"0x4033ac11443eedb5846695676bfa25249c14047a94156b886b46f81e30612cd6\",\"0xca9ee12369fd17cf59304f733f80bea3afad72a1a0a5b2f675d92e80454aa92c\",\"0xa5ae78531cc196f9493185ce9291cf9a5b93880329e43687c99d5febfa8a3275\",\"0x6cb9b2c7921b9e0d44f45070c7fde107598eff69f684b858e6ef614194aa5402\",\"0x4fdbc915d45bf1515232debe0a2333c53389f472eb28323bc4ee8d2bf363ddf1\",\"0xd8e77024ddb8da1824d4f3cd82a2941b043ee78d9a3bd8a11a6f64da72575095\",\"0xc83c88a70114fc83877093c5d6da72e488809200f202a5139e084ea98d33b41a\",\"0x91550f0db5856dbc88763362493df880a235b9e76476dcf6446814945664662f\",\"0x6d4feae6850b5c99a69acdfae8e6cecdf35bcbfd24765fb6206d6c8f96121d4d\",\"0xfcb4236f259bebd5cefdb6cd7ddd5141242f5a57ecb8edcd2618ab79cd32d7ef\",\"0xbdab5530ddb28b1c4baf67663c03cd4fdfa596a1315127d227cc80dd752393c3\",\"0x2e3e09f1eab7e189bf33b71860a8934609d77f7cd583d434e7ddd9b78a340f29\",\"0x3aec1d0ce09e822504c34df99c423b41f707b8d72e3d79aa0aa893d121f98e9c\",\"0x0676d890b910e13828688aebdb00e40e074156739425a34b26af0e49ac7d7d50\",\"0x68e8e7b175d9066a2b91fa05bd6482c9fe1745c005997b5bfe3959a0b60b8461\",\"0x57248b732c8291208a84a2d8b47be66da0f573d60d786c75043a546206e33e17\",\"0xf19bf53dbb232a0c3776fbcbe69ee1bcd326d879d2450f628245d7b0f778d1cd\",\"0xb4f492601c6279477511ce30bc413e243cb67617227c093561441f48eab98e7d\",\"0x3a8beda81b20c21b195b6dacf82ca4a0fed5dbe1b838f15aca4eca7176bb59d7\",\"0xb80a3755e32c7bf4fef1224b138a43c5f5a4141e2c42ada413fb92e64f2beab6\",\"0xac01509a9e392cb75e6848e02ef536dc9e0a0b94ca5adcaa8018dfc1d6323e89\",\"0xee17fd2fbd94e7f18a209966c147986a20f9fb8ea84f2b4aaeefbfc5fc226bcb\",\"0x6d599c313a857b9106488a6504847489d2a9bdbf02d1bea7592da84e8a23d0c6\",\"0xd2393907792ba346caa5ade1e4f22798025653ed4cc8bf80d830909b32af58d7\",\"0x8437f2cfa35934cfc062d509d66d95d7f34ca570df9b452ecbf11ef1b63492a7\",\"0x150cddaf920b8eea795e70f383297da98b98d925d22bbdbf979d3675cb4ef0c5\",\"0xa162b739ff323c5fca9e4f930c8ee58c70e1f8a32e2743f8c445afaaf0f14ef6\",\"0x55bf26e49b9f04098648c7424535d7142ea001b1d36ebe27f2cff9f55a37f609\",\"0x3c4815958737f14e2f1135a69df3bbf118790dc179a7dd51a14705fc3f4187b9\",\"0xdc0c177d304ad0dbb01510701b25ff05ee64fed61ad543a1f8539e9df28329e3\",\"0x740a0d45a61a5473ceb7bec72748ea0e83c76beec6028702f15946928e02b257\",\"0x9d37f1e072b1bfd3b7142f1eb69616aa8a30e15aa3d75fb9d516bcdd59f3f75a\",\"0x8242d3a1777ac3563fb3f79a574993e34fa8e62aacc00fd844fd7e25b15270a4\",\"0x0ee42f2220ad3a3ba4ce4456859fc1c87a68d5df6865781ea4c841ab3db7fe9e\",\"0x6bf1ed614978aea7df2be8ba9da47683ecba24f2b639609d906efbe99975f433\",\"0x42f0e3dc500f03740f3667db4d5f5edebe56609c4aa02c2a2e5b0b85179d7b95\",\"0x3593144b1d86e1b3d621409f07f084d11d2b16a2bf08b3bb9bbf6c361558c40e\",\"0xc663e5474a05ad68fc25b085c3dc503983c8c2a56378005f2229c2c1a766d25e\",\"0xe4de86dc3933604f50e54fea29b5fbb0c381e568815cd32455df4c54eb692b12\",\"0x81ed0f45d9e9e3c32bac6d383ca58ae9d2417a65a6f45f5cc6d67ed1b2096a68\",\"0x9a65a750a7ff5f1088d29350253054ca31750c559bc07489ee1331c04a101977\",\"0xf9e7d31a78b3cd522827a7c70cd866d7f325d8c0a44cef914664efd17ee49f35\",\"0x77b002e82728c9bd94b47f54e15d5247198f6dfd9d987de03ffb8791d48cbe36\",\"0x354629da0078817bf3928edc0c8473d4325b21788a6fcf120be94a352741bcf6\",\"0x399bafb13434ef62641903803bdba68e5dca8cd68c6508f449383657faaeaebe\",\"0xc1fc04aa0a10f78a58f4cbacffc685b664b36d2968c09010fc24380f2737a57a\",\"0xdfe3cf035e1838efeee3574db77cba534146084938223c8541cbbe213d40af7c\",\"0x3ee6542e53fb63930b533f27bc9c4e794355b6b3e6ddcb47db25a3eae3d63572\",\"0x147da4ed39fe9a7786f5e6edf18366b994217166ca5bd005af35d79fc2509219\",\"0x0549e33018331c775083b8dc1949c48c911f779665cb14d7fbaa1cb2f826bca1\",\"0x012a4ac85be2b458d94c1e06a5e37529bfdc671e74880de4f672720be51968cf\",\"0x2e87fb2fc0c2890a6af0a3830ab82f6b85dd5b582a85753333c8591c7d188cd6\",\"0xf871b3a24482c57d99ed94d15b80ce108bc28b12dc45d3f2d11db49ebec842e3\",\"0x233b52bbde962b1a71d5ebfdfd256355dfcb26a197f2642c0de0c9182cfd49e2\",\"0x7d48e442ca88a2b8d1be727f90714223bd2f4ae9d0882ebde37f654f6f56db1a\",\"0x0965a0f87fa5522ced10e1593618ddbf9778508de500bec1f7da2ee811862737\",\"0x81542044170a52d6c60a1e43c6f0f69824152b22d85eb7a4e37e9053527a55b1\",\"0xfdfecdb08aefa47eb92b725b35d70f02a5b09b369c2390522eb9ee9c78887aa5\",\"0x1172a29698985d4023e2f76e6a9ab24d29ef5c7665e6767263b79baccf8d964b\",\"0x7d1b74f0c78b5dc6fa5f41e15fd439e9727a4a9aa3821ded48192ed793cce163\",\"0x0606b05a5c949e1491c1c23ac50f2c6ffbc7742696ef5865d2de769ade7718af\",\"0x6fb7a99eea9e4b7c840db13754e0fb74fc12c2ae009978ae804619c9cc41af4b\",\"0x1f61245a0857601567a2da01f126877bbcc15a291792d461ed78e9f45aa63c93\",\"0x9ba7addf4e96f471b62e7837ed00ead8fa44a367a56d7587fbf031e87efbf70e\",\"0x4f36da0dac103cb26cac63b40de3b77c9c46ac88231ba405e127152118321c28\",\"0x6b02e863427207aaa1c33ad09194ae420c9a0964a2bb42a93c1ef34b9e974fe3\",\"0xebdd96aaab92b09fd242ebd0b8ff87c60f566231c81a70c4f566950a6346f9c9\",\"0xdd4f2cb885c3f98061a5df0193653ac432348d8e7ee3b8528f2f6a495e295daa\",\"0x92487d2d05b8a969ca4c80511d0f441a7c070940c5db4e0c3366f2e72732f39d\",\"0xba31cddc1c92e51edbca83c69abbf419aedcab63d1c7e07070ff74be828f37de\",\"0x27bd15d9daf183fe8fb68069c4fbcdccb720ea4d55d0b8434093d56d002c2b42\",\"0x70ab33aafc1ac7d959045ceedfa07bce4b8cc7f23344d36fb320fcaec042e567\",\"0x1ad131ef59867c0a58c92a25f46e053292b450495a3e662d4717c1651f0832d9\",\"0xfdaebfa92e83e8729c1c8088bdc37da730a65ee56dccadff8d514f101af5806e\",\"0x0aed0b44a6df846dcee1fd45b628eaa858124f43c9bc4bd706f71da7f1d9b7bf\",\"0x2ec34820635c2ad50270572cdfb7e2767495ce050d585d1de3f18459cf96969f\",\"0xdc9b618ccf07430d2418101312e3ab7e6ccab116334d8e55fb610c7f2151c6d5\",\"0xe2adbeb09d81cc8f25cbbad2951d2d91e00245f3e7b1ec952c987ffdd5452692\",\"0x89d4b185e49cca9baf9352703b979998c9d7783466ef725b0470e6559c1d9f63\",\"0x269941e7adc06ccf34d892fdfe49aecf3b769a045239564377f6ce1d64108c0e\",\"0xf13dbe9562a2d9fe707a1295fa98b993b3022163b7271dab823fb1978ee8e037\",\"0x70fab8b99eee0b0825c2084c14ee356ee980d00df0c3ecc9b2895bce42834fff\",\"0x68c18c4e9b5d0dbcf10d9acc205121c50a2e6455b42b9c3431de8d9d14e0562d\",\"0x4cb508adda202782faa51c065423506f8f2c7e875443a3f4dd168ccf469b2bd4\",\"0x32cc7e489d84349a8daecb9c258828f975a70eef6afb2b80e8044c59799d2b79\",\"0x713f2a649ca07c09f866fe6a4f790a9c345e9fb35c2b517b2a9203e50b39f8c2\",\"0x64365b6a17efd2158a211ed1cafe619f9d058805a00e456cc3ec73e3d3f985bd\",\"0x8c292a0ed00afc0dad4242a2f6383ae77dbdbfa6986b5803dba58228bed2a6f5\",\"0x660c3ad7cc9b9f5bec470fece30ff363bf1e97442f421740aca507d937e03cdb\",\"0x61bf8faf63c6412fa77d874e6b2874e4cf25147657865759784e2d267f7fbb80\",\"0x013e8252f772aea3f1ae18c600c8c6c0b4967def5a7458a2b1b0cc7cd9d77471\",\"0x9b0e0df00d8d52a6fc2c26f10a9de2d68103c24465bc3d9c504135693389176a\",\"0x16ac2c722b2db59a47416d019c4ea6f0dba4a1bd52f8a6d615b4ced72210f3c1\",\"0x295203e97862ebce4771834156951e3337c3bbfa7f30eb0bae4127f9613af9ea\",\"0xee37e054882884882d1e116d6674908074e861c51702407531484a0b52d7d360\",\"0x7ae12714f221d1e73f87c4f2e9a6e5775fb956544dbdd5f1846797f19a4b87b6\",\"0x8cb372673756fe6b1497da953322ca60bfcfc3762b15415e08127a7c098ddecf\",\"0xf0ac496992c4a659129c02701963058971ced8af0b93ef14c7a89d819e49eb39\",\"0xac58eacb45ba22745fbb2f7f656115545161ab2ea1f1f00756aa91e2be6751d1\",\"0x29bf4d52b7537e6291f53b64fad563996878ce7123e789f75ad1cc7500d081ca\",\"0xdeb06972fa0ea60460645a154686cabc636b2e01c2a726363ebd83e2d66288f4\",\"0x5075f7df2455215bde6a16e6c0466348a944409e2314b88b7b4f2c355b237157\",\"0xd519d8daba6ff7788f031befdc313fad87bbda2517a9da1899eaea38ce2f8ae6\",\"0x469e6cabd67ccb6007f91bc409ec13dec7d30cd4c4f4c08c636db877595372a2\",\"0x2d5f6f5491646465b937a995a2fcdb716b035d5a45b26994d632fdf22926800d\",\"0x13426e8eaad71d731908923ece5a7262486fc6ba4bac99df37ab23f456a105d1\",\"0x6e1335353e139a383d9ed0a5d2d18df2640badb1738cf8978a6f7276468ed7ff\",\"0x97d0927b539cd41724d3ac84e1d5e94b418f0711ec36d10c29d31fb9fd75724c\",\"0x13b22febaed9b45f49e2bc410f013e4c2d395db24a228639ae4cdae68fb74919\",\"0xfad784569b3330bc6ea8469e9a6ab37038f81c5d4c031d5390e1f571abd85bde\",\"0xe1359af68eee5fce0dda478881131072d23100455edd882c01cf028dc62a03d1\",\"0xe2ef653d0f852ba327c625fcb7de1f8c2f8e56debcf36c041abedf5b3411b045\",\"0xa93d9f4a977a7bb25016913baf240addbb7e5393692595597bf184ad763e103f\",\"0xbe00e6a947ebb54d7262527279c5d539ac2cdb31ecd1d94dd428c764b3fd4659\"],\"transactionsRoot\":\"0xc202b1d741fb893944152e9780754c43054b336fbc1ded86a385a79db98f2e94\",\"uncles\":[],\"withdrawals\":[{\"index\":\"0x78ce85\",\"validatorIndex\":\"0x4752d\",\"address\":\"0xb9d7934878b5fb9610b3fe8a5e441e8fad7e293f\",\"amount\":\"0xd5ab7c\"},{\"index\":\"0x78ce86\",\"validatorIndex\":\"0x4752e\",\"address\":\"0xb9d7934878b5fb9610b3fe8a5e441e8fad7e293f\",\"amount\":\"0xd5e995\"},{\"index\":\"0x78ce87\",\"validatorIndex\":\"0x4752f\",\"address\":\"0xb9d7934878b5fb9610b3fe8a5e441e8fad7e293f\",\"amount\":\"0xd60926\"},{\"index\":\"0x78ce88\",\"validatorIndex\":\"0x47530\",\"address\":\"0xb9d7934878b5fb9610b3fe8a5e441e8fad7e293f\",\"amount\":\"0xd5fda4\"},{\"index\":\"0x78ce89\",\"validatorIndex\":\"0x47531\",\"address\":\"0xb9d7934878b5fb9610b3fe8a5e441e8fad7e293f\",\"amount\":\"0xd5f4a6\"},{\"index\":\"0x78ce8a\",\"validatorIndex\":\"0x47532\",\"address\":\"0xb9d7934878b5fb9610b3fe8a5e441e8fad7e293f\",\"amount\":\"0xd5970c\"},{\"index\":\"0x78ce8b\",\"validatorIndex\":\"0x47533\",\"address\":\"0xb9d7934878b5fb9610b3fe8a5e441e8fad7e293f\",\"amount\":\"0xd5c67f\"},{\"index\":\"0x78ce8c\",\"validatorIndex\":\"0x47534\",\"address\":\"0xb9d7934878b5fb9610b3fe8a5e441e8fad7e293f\",\"amount\":\"0xd5acd2\"},{\"index\":\"0x78ce8d\",\"validatorIndex\":\"0x47535\",\"address\":\"0xb9d7934878b5fb9610b3fe8a5e441e8fad7e293f\",\"amount\":\"0xd5c392\"},{\"index\":\"0x78ce8e\",\"validatorIndex\":\"0x47536\",\"address\":\"0xcf432054d0648b820ef952d969cb7d67635b7e57\",\"amount\":\"0xd68a91\"},{\"index\":\"0x78ce8f\",\"validatorIndex\":\"0x47537\",\"address\":\"0xcf432054d0648b820ef952d969cb7d67635b7e57\",\"amount\":\"0xd5afd9\"},{\"index\":\"0x78ce90\",\"validatorIndex\":\"0x47538\",\"address\":\"0xcf432054d0648b820ef952d969cb7d67635b7e57\",\"amount\":\"0xd623ff\"},{\"index\":\"0x78ce91\",\"validatorIndex\":\"0x47539\",\"address\":\"0xcf432054d0648b820ef952d969cb7d67635b7e57\",\"amount\":\"0xd58af7\"},{\"index\":\"0x78ce92\",\"validatorIndex\":\"0x4753a\",\"address\":\"0xcf432054d0648b820ef952d969cb7d67635b7e57\",\"amount\":\"0x502617a\"},{\"index\":\"0x78ce93\",\"validatorIndex\":\"0x4753b\",\"address\":\"0xcf432054d0648b820ef952d969cb7d67635b7e57\",\"amount\":\"0xd5dc43\"},{\"index\":\"0x78ce94\",\"validatorIndex\":\"0x4753c\",\"address\":\"0x210b3cb99fa1de0a64085fa80e18c22fe4722a1b\",\"amount\":\"0xd52778\"}],\"withdrawalsRoot\":\"0x649d495a98f54d5e09178709832693ae7f3b0ab6495dff382f8c45f532b31e40\"}"

        @JvmStatic
        fun main(args: Array<String>) {
            val options = OptionsBuilder()
                .include(JsonBenchmark::class.java.simpleName)
                .addProfiler(GCProfiler::class.java)
                .warmupIterations(3)
                .measurementIterations(3)
                .build()

            Runner(options).run()
        }
    }

    @Serializable
    data class BlockWithHashesReflection(
        val baseFeePerGas: String,
        val difficulty: String,
        val extraData: String,
        val gasLimit: String,
        val gasUsed: String,
        val hash: String,
        val logsBloom: String,
        val miner: String,
        val mixHash: String,
        val nonce: String,
        val number: String,
        val parentHash: String,
        val receiptsRoot: String,
        val sha3Uncles: String,
        val size: String,
        val stateRoot: String,
        val timestamp: String,
        val totalDifficulty: String,
        val transactions: List<String>,
        val transactionsRoot: String,
        val uncles: List<String>,
        val withdrawals: List<WithdrawalPlain>,
        val withdrawalsRoot: String,
    )

    data class BlockWithHashesAnnotations(
        @param:JsonProperty("baseFeePerGas") val baseFeePerGas: String,
        @param:JsonProperty("difficulty") val difficulty: String,
        @param:JsonProperty("extraData") val extraData: String,
        @param:JsonProperty("gasLimit") val gasLimit: String,
        @param:JsonProperty("gasUsed") val gasUsed: String,
        @param:JsonProperty("hash") val hash: String,
        @param:JsonProperty("logsBloom") val logsBloom: String,
        @param:JsonProperty("miner") val miner: String,
        @param:JsonProperty("mixHash") val mixHash: String,
        @param:JsonProperty("nonce") val nonce: String,
        @param:JsonProperty("number") val number: String,
        @param:JsonProperty("parentHash") val parentHash: String,
        @param:JsonProperty("receiptsRoot") val receiptsRoot: String,
        @param:JsonProperty("sha3Uncles") val sha3Uncles: String,
        @param:JsonProperty("size") val size: String,
        @param:JsonProperty("stateRoot") val stateRoot: String,
        @param:JsonProperty("timestamp") val timestamp: String,
        @param:JsonProperty("totalDifficulty") val totalDifficulty: String,
        @param:JsonProperty("transactions") val transactions: List<String>,
        @param:JsonProperty("transactionsRoot") val transactionsRoot: String,
        @param:JsonProperty("uncles") val uncles: List<String>,
        @param:JsonProperty("withdrawals") val withdrawals: List<WithdrawalPlain>,
        @param:JsonProperty("withdrawalsRoot") val withdrawalsRoot: String,
    )

    @JsonDeserialize(using = BlockDeserializer::class)
    data class BlockWithHashes(
        val baseFeePerGas: String,
        val difficulty: String,
        val extraData: String,
        val gasLimit: String,
        val gasUsed: String,
        val hash: Hash,
        val logsBloom: String,
        val miner: Address,
        val mixHash: Hash,
        val nonce: String,
        val number: String,
        val parentHash: Hash,
        val receiptsRoot: Hash,
        val sha3Uncles: Hash,
        val size: String,
        val stateRoot: Hash,
        val timestamp: String,
        val totalDifficulty: String,
        val transactions: List<Hash>,
        val transactionsRoot: Hash,
        val uncles: List<Hash>,
        val withdrawals: List<Withdrawal>,
        val withdrawalsRoot: Hash,
    )

    data class Withdrawal(
        val index: String,
        val validatorIndex: String,
        val address: Address,
        val amount: String,
    )

    @Serializable
    data class WithdrawalPlain(
        val index: String,
        val validatorIndex: String,
        val address: String,
        val amount: String,
    )

    private class BlockDeserializer : StdDeserializer<BlockWithHashes>(BlockWithHashes::class.java) {
        override fun deserialize(p: JsonParser, ctxt: DeserializationContext): BlockWithHashes {
            lateinit var baseFeePerGas: String
            lateinit var difficulty: String
            lateinit var extraData: String
            lateinit var gasLimit: String
            lateinit var gasUsed: String
            lateinit var hash: Hash
            lateinit var logsBloom: String
            lateinit var miner: Address
            lateinit var mixHash: Hash
            lateinit var nonce: String
            lateinit var number: String
            lateinit var parentHash: Hash
            lateinit var receiptsRoot: Hash
            lateinit var sha3Uncles: Hash
            lateinit var size: String
            lateinit var stateRoot: Hash
            lateinit var timestamp: String
            lateinit var totalDifficulty: String
            lateinit var transactions: ArrayList<Hash>
            lateinit var transactionsRoot: Hash
            lateinit var uncles: ArrayList<Hash>
            lateinit var withdrawals: ArrayList<Withdrawal>
            lateinit var withdrawalsRoot: Hash

            val hashWriter = HashWriter()
            val addressWriter = AddressWriter()
            var token: JsonToken?
            while (p.nextToken().also { token = it } != null) {
                if (token!!.isStructStart || token!!.isStructEnd) {
                    continue
                }
                when (p.text) {
                    "baseFeePerGas" -> baseFeePerGas = p.nextTextValue()
                    "difficulty" -> difficulty = p.nextTextValue()
                    "extraData" -> extraData = p.nextTextValue()
                    "gasLimit" -> gasLimit = p.nextTextValue()
                    "gasUsed" -> gasUsed = p.nextTextValue()
                    "hash" -> hash = p.nextToken().let {
                        p.getText(hashWriter)
                        hashWriter.hash!!
                    }

                    "logsBloom" -> logsBloom = p.nextTextValue()
                    "miner" -> miner = p.nextToken().let {
                        p.getText(addressWriter)
                        addressWriter.address!!
                    }

                    "mixHash" -> mixHash = p.nextToken().let {
                        p.getText(hashWriter)
                        hashWriter.hash!!
                    }

                    "nonce" -> nonce = p.nextTextValue()
                    "number" -> number = p.nextTextValue()
                    "parentHash" -> parentHash = p.nextToken().let {
                        p.getText(hashWriter)
                        hashWriter.hash!!
                    }

                    "receiptsRoot" -> receiptsRoot = p.nextToken().let {
                        p.getText(hashWriter)
                        hashWriter.hash!!
                    }

                    "sha3Uncles" -> sha3Uncles = p.nextToken().let {
                        p.getText(hashWriter)
                        hashWriter.hash!!
                    }

                    "size" -> size = p.nextTextValue()
                    "stateRoot" -> stateRoot = p.nextToken().let {
                        p.getText(hashWriter)
                        hashWriter.hash!!
                    }

                    "timestamp" -> timestamp = p.nextTextValue()
                    "totalDifficulty" -> totalDifficulty = p.nextTextValue()
                    "transactions" -> {
                        transactions = ArrayList()
                        p.nextToken()
                        while (p.nextToken() != JsonToken.END_ARRAY) {
                            p.getText(hashWriter)
                            transactions.add(hashWriter.hash!!)
                        }
                    }

                    "uncles" -> {
                        uncles = ArrayList()
                        p.nextToken()
                        while (p.nextToken() != JsonToken.END_ARRAY) {
                            p.getText(hashWriter)
                            uncles.add(hashWriter.hash!!)
                        }
                    }

                    "transactionsRoot" ->
                        transactionsRoot =
                            p.nextToken().let {
                                p.getText(hashWriter)
                                hashWriter.hash!!
                            }

                    "withdrawals" -> {
                        withdrawals = ArrayList()
                        lateinit var index: String
                        lateinit var validatorIndex: String
                        lateinit var address: Address
                        lateinit var amount: String
                        p.nextToken()
                        while (p.nextToken().also { token = it } != JsonToken.END_ARRAY) {
                            if (token!!.isStructStart) {
                                continue
                            }

                            when (p.text) {
                                "index" -> index = p.nextTextValue()
                                "validatorIndex" -> validatorIndex = p.nextTextValue()
                                "address" -> {
                                    p.nextToken()
                                    p.getText(addressWriter)
                                    address = addressWriter.address!!
                                }
                                //"address" -> address = p.nextTextValue()
                                "amount" -> amount = p.nextTextValue()
                            }

                            if (token == JsonToken.END_OBJECT) {
                                withdrawals.add(Withdrawal(index, validatorIndex, address, amount))
                            }
                        }
                    }

                    "withdrawalsRoot" ->
                        withdrawalsRoot =
                            p.nextToken().let {
                                p.getText(hashWriter)
                                hashWriter.hash!!
                            }
                }
            }
            p.close()

            return BlockWithHashes(
                baseFeePerGas,
                difficulty,
                extraData,
                gasLimit,
                gasUsed,
                hash,
                logsBloom,
                miner,
                mixHash,
                nonce,
                number,
                parentHash,
                receiptsRoot,
                sha3Uncles,
                size,
                stateRoot,
                timestamp,
                totalDifficulty,
                transactions,
                transactionsRoot,
                uncles,
                withdrawals,
                withdrawalsRoot,
            )
        }
    }

    data class Address(val bytes: ByteArray) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Address

            return bytes.contentEquals(other.bytes)
        }

        override fun hashCode(): Int {
            return bytes.contentHashCode()
        }

        override fun toString(): String {
            return "0x" + FastHex.encodeWithoutPrefix(bytes)
        }
    }

    data class Hash(val bytes: ByteArray) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Hash

            return bytes.contentEquals(other.bytes)
        }

        override fun hashCode(): Int {
            return bytes.contentHashCode()
        }

        override fun toString(): String {
            return "0x" + FastHex.encodeWithoutPrefix(bytes)
        }
    }

    private class HexCharWriter(size: Int) : Writer() {
        private var buf = CharArray(size)
        private var count = 0

        override fun close() {
        }

        override fun flush() {
        }

        override fun write(cbuf: CharArray, off: Int, len: Int) {
            val newCount = count + len
            if (newCount > buf.size) {
                buf = buf.copyOf(max(buf.size shl 1, newCount))
            }
            System.arraycopy(cbuf, off, buf, count, len)
            count = newCount
        }

        fun toByteArray(): ByteArray {
            return FastHex.decode(buf).also { count = 0 }
        }
    }

    private class AddressWriter : Writer() {
        var address: Address? = null
            private set

        override fun close() {
        }

        override fun flush() {
        }

        override fun write(cbuf: CharArray, off: Int, len: Int) {
            address = Address(FastHex.decode(cbuf, off, len))
        }
    }

    private class HashWriter : Writer() {
        var hash: Hash? = null
            private set

        override fun close() {
        }

        override fun flush() {
        }

        override fun write(cbuf: CharArray, off: Int, len: Int) {
            hash = Hash(FastHex.decode(cbuf, off, len))
        }
    }
}
