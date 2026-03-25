import argparse
import traceback
import torch
import torch.nn.functional as F
import torchaudio
import math

# Monkey-patch F.scaled_dot_product_attention to use math operations for ONNX export
original_sdpa = getattr(F, 'scaled_dot_product_attention', None)
def patched_sdpa(query, key, value, attn_mask=None, dropout_p=0.0, is_causal=False, scale=None):
    L, S = query.size(-2), key.size(-2)
    scale_factor = 1 / math.sqrt(query.size(-1)) if scale is None else scale
    
    attn_weight = query @ key.transpose(-2, -1) * scale_factor
    
    if is_causal:
        assert attn_mask is None
        temp_mask = torch.ones(L, S, dtype=torch.bool, device=query.device).tril(diagonal=0)
        attn_weight.masked_fill_(temp_mask.logical_not(), float("-inf"))

    if attn_mask is not None:
        if attn_mask.dtype == torch.bool:
            attn_weight.masked_fill_(attn_mask.logical_not(), float("-inf"))
        else:
            attn_weight += attn_mask

    attn_weight = torch.softmax(attn_weight, dim=-1)
    if dropout_p > 0.0:
        attn_weight = torch.dropout(attn_weight, dropout_p, train=True)
    return attn_weight @ value

if original_sdpa is not None:
    F.scaled_dot_product_attention = patched_sdpa
    torch.nn.functional.scaled_dot_product_attention = patched_sdpa

try:
    torchaudio.set_audio_backend("soundfile")
except RuntimeError:
    pass

_original_set_audio_backend = torchaudio.set_audio_backend
def _patched_set_audio_backend(backend):
    available_backends = torchaudio.list_audio_backends()
    if backend not in available_backends:
        if 'soundfile' in available_backends:
            return _original_set_audio_backend('soundfile')
        else:
            raise RuntimeError(f'Backend "{backend}" is not available. Available: {available_backends}')
    return _original_set_audio_backend(backend)

torchaudio.set_audio_backend = _patched_set_audio_backend

from models.WavLM_Nes2Net_X_SeLU import WavLM_Nes2Net_SE_cat_SeLU as Model

def main():
    device = torch.device("cpu")
    parser = argparse.ArgumentParser()
    parser.add_argument("--agg", type=str, default='SEA')
    parser.add_argument("--dilation", type=int, default=1)
    parser.add_argument("--pool_func", type=str, default='mean')
    parser.add_argument("--SE_ratio", type=int, nargs='+', default=[1])
    parser.add_argument("--Nes_ratio", type=int, nargs='+', default=[8, 8])
    args = parser.parse_args([])
    
    model = Model(args, device).to(device)
    dummy_input = torch.randn(1, 16000, dtype=torch.float32)
    
    try:
        torch.onnx.export(
            model,
            dummy_input,
            "test.onnx",
            input_names=['input_audio'],
            output_names=['output_score'],
            dynamic_axes={
                'input_audio': {0: 'batch_size', 1: 'sequence_length'},
                'output_score': {0: 'batch_size'}
            },
            opset_version=17,
            do_constant_folding=False,
            export_params=True,
        )
        print("ONNX EXPORT SUCCESSFUL!")
    except Exception as e:
        traceback.print_exc()

if __name__ == "__main__":
    main()
