#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Generate embeddings for all landmarks using Ollama nomic-embed-text model.

This script is MANDATORY for vector search functionality.
Run this after loading landmarks into the database.

Usage:
    python landmark_model/generate_embeddings.py [--batch-size 100]
"""

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))

from database import LandmarksDB, EMBEDDING_MODEL


def main():
    print("=" * 70)
    print("🧠 LandmarkLens Vector Embeddings Generator")
    print("=" * 70)
    print(f"\n📍 Model: {EMBEDDING_MODEL}")
    print("📍 Dimension: 768")
    print("📍 Type: Semantic embeddings\n")

    db = LandmarksDB()

    # Check if Ollama is available
    try:
        import ollama
        print("✅ Ollama client available")
    except ImportError:
        print("❌ Ollama module not found. Install: pip install ollama")
        return False

    # Check database
    stats = db.get_stats()
    print(f"📊 Database: {stats['total_landmarks']} landmarks loaded")
    print(f"🗄️  Current embeddings: {stats['landmarks_with_embeddings']}\n")

    if stats['landmarks_with_embeddings'] == stats['total_landmarks']:
        print("✅ All landmarks already have embeddings!")
        db.close()
        return True

    # Generate embeddings batch by batch
    print("⏳ Starting embedding generation...\n")
    print("=" * 70)
    
    batch_size = 100
    total_to_generate = stats['total_landmarks'] - stats['landmarks_with_embeddings']
    batches_needed = (total_to_generate + batch_size - 1) // batch_size
    
    for batch_num in range(batches_needed):
        print(f"\n📦 Batch {batch_num + 1}/{batches_needed}")
        print("-" * 70)
        
        try:
            count = db.generate_embeddings_batch(batch_size=batch_size)
            if count == 0:
                print("ℹ️  No more landmarks to process")
                break
        except KeyboardInterrupt:
            print("\n\n⚠️  Generation interrupted by user")
            db.close()
            return False
        except Exception as e:
            print(f"\n❌ Error during generation: {e}")
            db.close()
            return False

    # Final check
    print("\n" + "=" * 70)
    stats = db.get_stats()
    print(f"📊 Final Statistics")
    print(f"{'=' * 70}")
    print(f"  Total landmarks: {stats['total_landmarks']}")
    print(f"  With embeddings: {stats['landmarks_with_embeddings']}")
    print(f"  Database size: {stats['db_size_mb']:.2f} MB")
    
    if stats['landmarks_with_embeddings'] == stats['total_landmarks']:
        print("\n✅ VECTOR EMBEDDINGS GENERATION COMPLETE!")
        print("🎉 Your database is ready for semantic search!\n")
        print("   Usage:")
        print("   ------")
        print("   from landmark_model.database import LandmarksDB")
        print("   ")
        print("   db = LandmarksDB()")
        print("   results = db.search_by_embedding('iglesia histórica', limit=5)")
        print("   ")
        db.close()
        return True
    else:
        remaining = stats['total_landmarks'] - stats['landmarks_with_embeddings']
        print(f"\n⚠️  {remaining} landmarks still need embeddings")
        print("   Re-run this script to continue generation")
        db.close()
        return False


if __name__ == "__main__":
    success = main()
    sys.exit(0 if success else 1)
